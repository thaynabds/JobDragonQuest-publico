package dev.jobdragonquest.sources;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import dev.jobdragonquest.ApiTestSupport;
import dev.jobdragonquest.common.domain.WorkMode;
import dev.jobdragonquest.sources.collect.CollectedJob;
import dev.jobdragonquest.sources.collect.GreenhouseClient;

/** Modo de automação por perfil (issue #112). */
class AutomationModeTest extends ApiTestSupport {

    @MockitoBean
    private GreenhouseClient greenhouse;

    private static final List<CollectedJob> BOARD = List.of(new CollectedJob("1", "Desenvolvedora Java Júnior",
            "https://boards.greenhouse.io/modo/jobs/1", "Remote", null, WorkMode.REMOTE, "Buscamos pessoa com Java e Spring Boot."));

    private void collect(String token, String mode) throws Exception {
        String modeField = mode == null ? "" : ",\"automationMode\":\"" + mode + "\"";
        String profile = read(postJson(token, "/api/v1/profiles", """
                {"name":"Java","seniority":"JUNIOR","minMatchScore":60,"workModes":["REMOTE"],"desiredTitles":["Desenvolvedora Java"],
                 "skills":[{"name":"Java","level":4},{"name":"Spring Boot","level":3}]%s}""".formatted(modeField))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.automationMode").value(mode == null ? "REVIEW" : mode)), "$.id");
        String source = read(postJson(token, "/api/v1/sources/bulk", """
                {"profileIds":["%s"],"sources":[{"name":"Modo","kind":"GREENHOUSE","identifier":"modo"}]}""".formatted(profile))
                .andExpect(status().isCreated()), "$.created[0].id");
        postJson(token, "/api/v1/sources/" + source + "/run", "").andExpect(jsonPath("$.created").value(1));
    }

    @Test
    void reviewIsTheDefaultAndCreatesRecommended() throws Exception {
        when(greenhouse.kind()).thenReturn(SourceKind.GREENHOUSE);
        when(greenhouse.fetch("modo")).thenReturn(BOARD);
        String token = newUser();
        collect(token, null);
        getJson(token, "/api/v1/applications?state=RECOMMENDED").andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void offKeepsOnlyTheJob() throws Exception {
        when(greenhouse.kind()).thenReturn(SourceKind.GREENHOUSE);
        when(greenhouse.fetch("modo")).thenReturn(BOARD);
        String token = newUser();
        collect(token, "OFF");
        getJson(token, "/api/v1/applications").andExpect(jsonPath("$.totalElements").value(0));
        getJson(token, "/api/v1/jobs").andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void automaticCreatesPreparedButNeverSubmitted() throws Exception {
        when(greenhouse.kind()).thenReturn(SourceKind.GREENHOUSE);
        when(greenhouse.fetch("modo")).thenReturn(BOARD);
        String token = newUser();
        collect(token, "AUTOMATIC");
        getJson(token, "/api/v1/applications?state=PREPARED").andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].origin").value("AUTOMATION"));
        getJson(token, "/api/v1/applications?state=SUBMITTED").andExpect(jsonPath("$.totalElements").value(0));
        postJson(token, "/api/v1/profiles", """
                {"name":"X","seniority":"JUNIOR","automationMode":"SEND_EVERYTHING"}""").andExpect(status().isBadRequest());
    }
}
