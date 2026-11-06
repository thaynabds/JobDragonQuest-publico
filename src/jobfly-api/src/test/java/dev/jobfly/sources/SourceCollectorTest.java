package dev.jobfly.sources;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import dev.jobfly.ApiTestSupport;
import dev.jobfly.common.domain.WorkMode;
import dev.jobfly.sources.collect.CollectedJob;
import dev.jobfly.sources.collect.CollectionException;
import dev.jobfly.sources.collect.GreenhouseClient;

/** Critérios de aceite da issue #10, com o adaptador do Greenhouse simulado (sem rede). */
class SourceCollectorTest extends ApiTestSupport {

    @MockitoBean
    private GreenhouseClient greenhouse;

    @Autowired
    private SourceCollector collector;

    @Autowired
    private WatchedSourceRepository repository;

    private static final List<CollectedJob> BOARD = List.of(
            new CollectedJob("1", "Desenvolvedora Java Júnior", "https://boards.greenhouse.io/acme/jobs/1", "Remote", null,
                    WorkMode.REMOTE, "Buscamos pessoa com Java e Spring Boot."),
            new CollectedJob("2", "Gerente de Vendas", "https://boards.greenhouse.io/acme/jobs/2", "São Paulo", null,
                    WorkMode.ONSITE, "Vendas B2B"),
            new CollectedJob("3", "Desenvolvedor Java Pleno", "javascript:alert(1)", "Remote", null, WorkMode.REMOTE, "Java"),
            new CollectedJob("4", "Desenvolvedor Java Sênior", "https://boards.greenhouse.io/acme/jobs/4", "Remote", null,
                    WorkMode.REMOTE, "Java, Kotlin, Kubernetes, Kafka e Terraform"));

    private String setup(String token) throws Exception {
        String profile = read(postJson(token, "/api/v1/profiles", """
                {"name":"Java","seniority":"JUNIOR","minMatchScore":60,"workModes":["REMOTE"],
                 "desiredTitles":["Desenvolvedora Java"],
                 "skills":[{"name":"Java","level":4},{"name":"Spring Boot","level":3}]}"""), "$.id");
        return read(postJson(token, "/api/v1/sources/bulk", """
                {"profileIds":["%s"],"sources":[{"name":"Acme","kind":"GREENHOUSE","identifier":"acme"}]}""".formatted(profile))
                .andExpect(status().isCreated()), "$.created[0].id");
    }

    @Test
    void collectsFiltersDeduplicatesAndRecommends() throws Exception {
        when(greenhouse.kind()).thenReturn(SourceKind.GREENHOUSE);
        when(greenhouse.fetch("acme")).thenReturn(BOARD);
        String token = newUser();
        String source = setup(token);

        // Sem palavras-chave na fonte, filtra pelos cargos desejados do perfil: "Gerente de Vendas" fica de fora.
        postJson(token, "/api/v1/sources/" + source + "/run", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.found").value(3))
                .andExpect(jsonPath("$.created").value(2))
                .andExpect(jsonPath("$.recommended").value(1))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("1 ignorada(s)")));

        getJson(token, "/api/v1/jobs?sourceId=" + source)
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].watchedSourceId").value(source));
        getJson(token, "/api/v1/applications?state=RECOMMENDED")
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Desenvolvedora Java Júnior"))
                .andExpect(jsonPath("$.content[0].origin").value("AUTOMATION"));

        // Segunda coleta: nada duplica.
        postJson(token, "/api/v1/sources/" + source + "/run", "")
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.recommended").value(0));
        getJson(token, "/api/v1/sources/" + source)
                .andExpect(jsonPath("$.lastStatus").value("OK"))
                .andExpect(jsonPath("$.lastFound").value(3))
                .andExpect(jsonPath("$.due").value(false));
        getJson(token, "/api/v1/sources/" + source + "/runs").andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void failureIsRecordedAndBacksOff() throws Exception {
        when(greenhouse.kind()).thenReturn(SourceKind.GREENHOUSE);
        when(greenhouse.fetch(anyString())).thenThrow(new CollectionException("Board não encontrado no ATS. Confira o identificador."));
        String token = newUser();
        String source = setup(token);

        postJson(token, "/api/v1/sources/" + source + "/run", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.message").value("Board não encontrado no ATS. Confira o identificador."));
        WatchedSource saved = repository.findById(UUID.fromString(source)).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(saved.getConsecutiveFailures()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(saved.getNextRunAt()).isAfter(Instant.now().plusSeconds(5 * 3600));
    }

    @Test
    void schedulerCollectsDueSourcesOnceAndRespectsOwnership() throws Exception {
        when(greenhouse.kind()).thenReturn(SourceKind.GREENHOUSE);
        when(greenhouse.fetch("acme")).thenReturn(BOARD);
        String token = newUser();
        String source = setup(token);

        org.assertj.core.api.Assertions.assertThat(collector.runDue()).isGreaterThanOrEqualTo(1);
        getJson(token, "/api/v1/sources/" + source).andExpect(jsonPath("$.lastStatus").value("OK"));
        // Já coletada: não está mais vencida, então o próximo ciclo não a pega.
        collector.runDue();
        getJson(token, "/api/v1/sources/" + source + "/runs").andExpect(jsonPath("$.length()").value(1));

        postJson(newUser(), "/api/v1/sources/" + source + "/run", "").andExpect(status().isNotFound());
        getJson(newUser(), "/api/v1/sources/" + source + "/runs").andExpect(status().isNotFound());
    }

    @Test
    void assistedSourcesAreNotCollectedAutomatically() throws Exception {
        String token = newUser();
        String source = read(postJson(token, "/api/v1/sources/bulk", """
                {"sources":[{"name":"LinkedIn","kind":"LINKEDIN","url":"https://www.linkedin.com/jobs/search/?keywords=java"}]}"""),
                "$.created[0].id");
        postJson(token, "/api/v1/sources/" + source + "/run", "")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("assisted_source"));
    }
}
