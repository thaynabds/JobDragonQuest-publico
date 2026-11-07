package dev.jobdragonquest.settings;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import dev.jobdragonquest.ApiTestSupport;

/** Preferências de acessibilidade na conta (issue #126). */
class AccessibilitySettingsTest extends ApiTestSupport {

    private ResultActions save(String token, String json) throws Exception {
        return mvc.perform(put("/api/v1/me/accessibility").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void savesAndReturnsOnAnotherSessionOfTheSameAccount() throws Exception {
        String token = newUser();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/me/accessibility")
                .header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
        save(token, "{\"theme\":\"high_contrast\",\"colorFilter\":\"deuteranopia\",\"fontScale\":130,\"dyslexiaFont\":true,\"readingGuide\":false}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.theme").value("high_contrast"));
        save(token, "{\"theme\":\"kuro\",\"fontScale\":120}").andExpect(status().isOk());
        getJson(token, "/api/v1/me/accessibility")
                .andExpect(jsonPath("$.theme").value("kuro"))
                .andExpect(jsonPath("$.fontScale").value(120));
        // Outra conta não vê.
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/me/accessibility")
                .header("Authorization", "Bearer " + newUser())).andExpect(status().isNoContent());
    }

    @Test
    void refusesUnknownKeysAndValues() throws Exception {
        String token = newUser();
        for (String bad : new String[] {"{\"theme\":\"</style><script>\"}", "{\"fontScale\":999}", "{\"fontScale\":\"120\"}",
                "{\"classe\":true}", "{\"dyslexiaFont\":\"sim\"}", "[1,2]", "\"texto\"", "{\"colorFilter\":\"url(javascript:x)\"}"}) {
            save(token, bad).andExpect(status().isBadRequest());
        }
        save(null, "{\"theme\":\"dark\"}").andExpect(status().isUnauthorized());
    }
}
