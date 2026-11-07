package dev.jobdragonquest.jobs.link;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import dev.jobdragonquest.ApiTestSupport;

/** Rascunho de vaga a partir de um link, com a página simulada (sem rede). */
class JobLinkTest extends ApiTestSupport {

    @MockitoBean
    private SafeWebFetcher fetcher;

    @Test
    void returnsDraftWithMentionedSkillsAndDoesNotSave() throws Exception {
        when(fetcher.fetchHtml(any())).thenReturn("""
                <script type="application/ld+json">{"@type":"JobPosting","title":"Desenvolvedora Java",
                 "hiringOrganization":{"name":"Empresa Link"},"description":"Java, Spring Boot e Docker"}</script>""");
        String token = newUser();
        postJson(token, "/api/v1/jobs/from-link", """
                {"url":"https://www.empresa.test/vagas/1"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.extractedFrom").value("JSON_LD"))
                .andExpect(jsonPath("$.job.source").value("LINK"))
                .andExpect(jsonPath("$.job.company").value("Empresa Link"))
                .andExpect(jsonPath("$.job.requirements[*].skill").value(org.hamcrest.Matchers.hasItems("java", "docker")));
        getJson(token, "/api/v1/jobs").andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void pageWithoutJobDataAsksForManualEntry() throws Exception {
        when(fetcher.fetchHtml(any())).thenReturn("<html><body>nada</body></html>");
        postJson(newUser(), "/api/v1/jobs/from-link", """
                {"url":"https://www.empresa.test/"}""")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("link_no_job"));
    }
}
