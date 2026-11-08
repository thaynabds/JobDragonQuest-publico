package dev.jobdragonquest.e2e;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import dev.jobdragonquest.ApiTestSupport;
import dev.jobdragonquest.common.domain.WorkMode;
import dev.jobdragonquest.sources.SourceKind;
import dev.jobdragonquest.sources.collect.CollectedJob;
import dev.jobdragonquest.sources.collect.GreenhouseClient;

/**
 * Issue #159: teste ponta a ponta do fluxo principal pela API, com a aplicação inteira (segurança, banco, migrações,
 * coletor, motor de compatibilidade e máquina de estados). Só o ATS é simulado, para não depender de rede:
 * conta → perfil → fonte vigiada → coleta → vaga analisada → candidatura recomendada → envio → confirmação por
 * evidência → entrevista → histórico, visão geral e isolamento de outra conta.
 */
class FluxoPrincipalE2ETest extends ApiTestSupport {

    @MockitoBean
    private GreenhouseClient greenhouse;

    @Test
    void coletaAnaliseECandidaturaDePontaAPonta() throws Exception {
        when(greenhouse.kind()).thenReturn(SourceKind.GREENHOUSE);
        when(greenhouse.fetch("acme-e2e")).thenReturn(List.of(
                new CollectedJob("10", "Desenvolvedora Java Júnior", "https://boards.greenhouse.io/acme-e2e/jobs/10", "Remote", null,
                        WorkMode.REMOTE, "Buscamos pessoa com Java e Spring Boot."),
                new CollectedJob("11", "Analista de Marketing", "https://boards.greenhouse.io/acme-e2e/jobs/11", "Recife", null,
                        WorkMode.ONSITE, "Campanhas")));
        String token = newUser();

        // Perfil e fonte vigiada.
        String profile = read(postJson(token, "/api/v1/profiles", """
                {"name":"Java","seniority":"JUNIOR","minMatchScore":60,"workModes":["REMOTE"],
                 "desiredTitles":["Desenvolvedora Java"],
                 "skills":[{"name":"Java","level":4},{"name":"Spring Boot","level":3}]}""").andExpect(status().isCreated()), "$.id");
        String source = read(postJson(token, "/api/v1/sources/bulk", """
                {"profileIds":["%s"],"sources":[{"name":"Acme","kind":"GREENHOUSE","identifier":"acme-e2e"}]}""".formatted(profile))
                .andExpect(status().isCreated()), "$.created[0].id");

        // Coleta: só a vaga do cargo desejado entra, e ela vira recomendação.
        postJson(token, "/api/v1/sources/" + source + "/run", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.recommended").value(1));

        // Análise: a vaga coletada é compatível com o perfil, com justificativa.
        String job = read(getJson(token, "/api/v1/jobs?sourceId=" + source).andExpect(jsonPath("$.totalElements").value(1)), "$.content[0].id");
        getJson(token, "/api/v1/jobs/" + job + "/matches/" + profile)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendation").value("APPLY"))
                .andExpect(jsonPath("$.score").value(org.hamcrest.Matchers.greaterThanOrEqualTo(60)));

        // Candidatura: recomendada pela automação, enviada pela pessoa, confirmada e com entrevista por evidência.
        String application = read(getJson(token, "/api/v1/applications?state=RECOMMENDED")
                .andExpect(jsonPath("$.totalElements").value(1)), "$.content[0].id");
        for (String step : List.of(
                "{\"toState\":\"SUBMITTED\",\"source\":\"MANUAL\",\"confidence\":1,\"evidence\":\"Enviei pelo site da empresa\"}",
                "{\"toState\":\"CONFIRMED\",\"source\":\"EMAIL\",\"confidence\":0.95,\"evidence\":\"Recebemos sua candidatura\"}",
                "{\"toState\":\"INTERVIEW_SCHEDULED\",\"source\":\"MANUAL\",\"confidence\":1,\"evidence\":\"Entrevista marcada para segunda\"}")) {
            postJson(token, "/api/v1/applications/" + application + "/transitions", step).andExpect(status().isOk());
        }
        getJson(token, "/api/v1/applications/" + application)
                .andExpect(jsonPath("$.application.state").value("INTERVIEW_SCHEDULED"))
                .andExpect(jsonPath("$.application.company").value("Acme"))
                .andExpect(jsonPath("$.events.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(3)));
        getJson(token, "/api/v1/dashboard/summary").andExpect(status().isOk());

        // Isolamento: outra conta não vê nada disso.
        String intruder = newUser();
        getJson(intruder, "/api/v1/applications/" + application).andExpect(status().isNotFound());
        getJson(intruder, "/api/v1/jobs/" + job + "/matches/" + profile).andExpect(status().isNotFound());
    }
}
