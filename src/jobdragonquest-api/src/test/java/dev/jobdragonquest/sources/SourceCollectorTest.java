package dev.jobdragonquest.sources;

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

import dev.jobdragonquest.ApiTestSupport;
import dev.jobdragonquest.common.domain.WorkMode;
import dev.jobdragonquest.sources.collect.CollectedJob;
import dev.jobdragonquest.sources.collect.CollectionException;
import dev.jobdragonquest.sources.collect.GreenhouseClient;

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

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    /**
     * Os testes dividem o banco: fontes vencidas de outras classes entrariam no lote do agendador. Adia todas as outras
     * para o teste enxergar só a própria fonte, seja qual for a ordem em que as classes rodam.
     */
    private void onlyThisSourceDue(UUID source) {
        jdbc.update("update watched_source set next_run_at = ? where id <> ?",
                java.sql.Timestamp.from(Instant.now().plusSeconds(30L * 24 * 3600)), source);
    }

    @Test
    void schedulerCollectsDueSourcesOnceAndRespectsOwnership() throws Exception {
        when(greenhouse.kind()).thenReturn(SourceKind.GREENHOUSE);
        when(greenhouse.fetch("acme")).thenReturn(BOARD);
        String token = newUser();
        String source = setup(token);
        onlyThisSourceDue(UUID.fromString(source));

        org.assertj.core.api.Assertions.assertThat(collector.runDue()).isGreaterThanOrEqualTo(1);
        getJson(token, "/api/v1/sources/" + source).andExpect(jsonPath("$.lastStatus").value("OK"));
        // Já coletada: não está mais vencida, então o próximo ciclo não a pega.
        collector.runDue();
        getJson(token, "/api/v1/sources/" + source + "/runs").andExpect(jsonPath("$.length()").value(1));

        postJson(newUser(), "/api/v1/sources/" + source + "/run", "").andExpect(status().isNotFound());
        getJson(newUser(), "/api/v1/sources/" + source + "/runs").andExpect(status().isNotFound());
    }

    @Autowired
    private org.springframework.transaction.support.TransactionTemplate transaction;

    /** Critérios da issue #31: só uma coleta por fonte, e a reserva é liberada ao terminar. */
    @Test
    void onlyOneCollectionPerSourceAtATime() throws Exception {
        when(greenhouse.kind()).thenReturn(SourceKind.GREENHOUSE);
        when(greenhouse.fetch("acme")).thenReturn(BOARD);
        String token = newUser();
        UUID source = UUID.fromString(setup(token));
        onlyThisSourceDue(source);
        Instant now = Instant.now();

        // Simula uma coleta em andamento (ex.: o agendador pegou a fonte).
        transaction.executeWithoutResult(status -> repository.reserve(source, now, now.plusSeconds(600)));
        getJson(token, "/api/v1/sources/" + source).andExpect(jsonPath("$.running").value(true));
        postJson(token, "/api/v1/sources/" + source + "/run", "")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("collection_running"));
        org.assertj.core.api.Assertions.assertThat(collector.runDue()).as("o agendador também respeita a reserva").isZero();

        // Reserva vencida (coleta interrompida) não trava a fonte.
        transaction.executeWithoutResult(status -> repository.release(source));
        transaction.executeWithoutResult(status -> repository.reserve(source, now.minusSeconds(1200), now.minusSeconds(600)));
        postJson(token, "/api/v1/sources/" + source + "/run", "").andExpect(status().isOk()).andExpect(jsonPath("$.created").value(2));

        // Ao terminar, a reserva é liberada: outra coleta pode rodar.
        getJson(token, "/api/v1/sources/" + source).andExpect(jsonPath("$.running").value(false));
        postJson(token, "/api/v1/sources/" + source + "/run", "").andExpect(status().isOk()).andExpect(jsonPath("$.created").value(0));
    }

    @Test
    void reservationIsReleasedEvenWhenTheAtsFails() throws Exception {
        when(greenhouse.kind()).thenReturn(SourceKind.GREENHOUSE);
        when(greenhouse.fetch(anyString())).thenThrow(new CollectionException("O ATS respondeu com status 503"));
        String token = newUser();
        String source = setup(token);
        postJson(token, "/api/v1/sources/" + source + "/run", "").andExpect(jsonPath("$.status").value("FAILED"));
        getJson(token, "/api/v1/sources/" + source).andExpect(jsonPath("$.running").value(false));
        postJson(token, "/api/v1/sources/" + source + "/run", "").andExpect(status().isOk());
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
