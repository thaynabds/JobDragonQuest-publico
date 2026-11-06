package dev.jobfly.profiles;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import dev.jobfly.ApiTestSupport;

class ProfileIntegrationTest extends ApiTestSupport {

    @Test
    void createReadListUpdateDelete() throws Exception {
        String token = newUser();
        var created = postJson(token, "/api/v1/profiles", JAVA_PROFILE)
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.skills[1].name").value("Spring-Boot"))
                .andExpect(jsonPath("$.entries[0].url").value("https://observatorio.grupotp.polyclub.com.br/"))
                .andExpect(jsonPath("$.version").value(0));
        String id = read(created, "$.id");

        getJson(token, "/api/v1/profiles").andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].skillCount").value(3));

        putJson(token, "/api/v1/profiles/" + id, JAVA_PROFILE.replace("\"minMatchScore\":60", "\"minMatchScore\":70")
                .replaceFirst("\\}$", ",\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minMatchScore").value(70))
                .andExpect(jsonPath("$.version").value(1));

        deleteJson(token, "/api/v1/profiles/" + id).andExpect(status().isNoContent());
        getJson(token, "/api/v1/profiles/" + id).andExpect(status().isNotFound());
    }

    @Test
    void staleVersionIsRejected() throws Exception {
        String token = newUser();
        String id = read(postJson(token, "/api/v1/profiles", JAVA_PROFILE), "$.id");
        String withVersion = JAVA_PROFILE.replaceFirst("\\}$", ",\"version\":0}");
        putJson(token, "/api/v1/profiles/" + id, withVersion).andExpect(status().isOk());
        putJson(token, "/api/v1/profiles/" + id, withVersion)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("stale_version"));
    }

    @Test
    void anotherUserCannotSeeChangeOrDeleteProfile() throws Exception {
        String owner = newUser();
        String intruder = newUser();
        String id = read(postJson(owner, "/api/v1/profiles", JAVA_PROFILE), "$.id");

        getJson(intruder, "/api/v1/profiles/" + id).andExpect(status().isNotFound());
        putJson(intruder, "/api/v1/profiles/" + id, JAVA_PROFILE.replaceFirst("\\}$", ",\"version\":0}"))
                .andExpect(status().isNotFound());
        deleteJson(intruder, "/api/v1/profiles/" + id).andExpect(status().isNotFound());
        getJson(intruder, "/api/v1/profiles").andExpect(jsonPath("$.length()").value(0));
        getJson(owner, "/api/v1/profiles/" + id).andExpect(status().isOk());
    }

    @Test
    void duplicateNameSkillAndBadDataAreRejected() throws Exception {
        String token = newUser();
        postJson(token, "/api/v1/profiles", JAVA_PROFILE).andExpect(status().isCreated());
        postJson(token, "/api/v1/profiles", JAVA_PROFILE.replace("Desenvolvedora Java\",\"seniority", "desenvolvedora java\",\"seniority"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("profile_name_taken"));

        postJson(token, "/api/v1/profiles", """
                {"name":"Dup","seniority":"JUNIOR","skills":[{"name":"JS","level":3},{"name":"javascript","level":2}]}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("duplicate_skill"));

        postJson(token, "/api/v1/profiles", """
                {"name":"XSS","seniority":"JUNIOR","entries":[{"kind":"PROJECT","title":"x","url":"javascript:alert(1)"}]}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_url"));

        postJson(token, "/api/v1/profiles", """
                {"name":"Datas","seniority":"JUNIOR","entries":[{"kind":"EXPERIENCE","title":"x",
                 "startDate":"2026-05-01","endDate":"2025-01-01"}]}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_period"));

        postJson(token, "/api/v1/profiles", """
                {"name":"Nivel","seniority":"JUNIOR","skills":[{"name":"Java","level":9}]}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value(org.hamcrest.Matchers.startsWith("skills[0].level")));

        postJson(token, "/api/v1/profiles", "{isso não é json")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("malformed_request"));
    }
}
