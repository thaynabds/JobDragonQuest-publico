package dev.jobdragonquest.profiles;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import dev.jobdragonquest.ApiTestSupport;

/** Issue #172: opções de busca, candidatura e currículo salvas no perfil, validadas e isoladas por conta. */
class ProfileOptionsTest extends ApiTestSupport {

    private static String withOptions(String options) {
        return JAVA_PROFILE.substring(0, JAVA_PROFILE.lastIndexOf('}')) + ",\"options\":" + options + "}";
    }

    @Test
    void savesAndReturnsTheOptionsOnlyToTheOwner() throws Exception {
        String token = newUser();
        String id = read(postJson(token, "/api/v1/profiles", withOptions("""
                {"targetSeniorities":["INTERN","JUNIOR","MID"],"locationRadiusKm":50,"jobLanguages":["pt","en"],
                 "coverLetterMode":"GENERATED","phoneCountryCode":"+55","phone":"000","phoneWhatsapp":true,
                 "socialName":"Nome Social","diversity":["WOMAN"],"softSkills":["Comunicação"],"birthDate":"2000-01-31",
                 "military":"PREFER_NOT_TO_SAY","salaryMin":1000,"salaryMax":2000,
                 "contactHours":[{"day":"MONDAY","slots":[{"start":"08:00","end":"12:00"}]}],"resumeVisible":["CONTACT","SOFT_SKILLS"]}"""))
                .andExpect(status().isCreated()), "$.id");
        getJson(token, "/api/v1/profiles/" + id)
                .andExpect(jsonPath("$.options.targetSeniorities.length()").value(3))
                .andExpect(jsonPath("$.options.socialName").value("Nome Social"))
                .andExpect(jsonPath("$.options.birthDate").value("2000-01-31"))
                .andExpect(jsonPath("$.options.contactHours[0].slots[0].end").value("12:00"));
        getJson(newUser(), "/api/v1/profiles/" + id).andExpect(status().isNotFound());
    }

    @Test
    void refusesInvalidOptions() throws Exception {
        String token = newUser();
        postJson(token, "/api/v1/profiles", withOptions("{\"contactEmail\":\"nao-e-email\"}")).andExpect(status().isBadRequest());
        postJson(token, "/api/v1/profiles", withOptions("{\"contactHours\":[{\"day\":\"MONDAY\",\"slots\":[{\"start\":\"25:00\",\"end\":\"12:00\"}]}]}"))
                .andExpect(status().isBadRequest());
        postJson(token, "/api/v1/profiles", withOptions("{\"nationalities\":[\"a\",\"b\",\"c\",\"d\"]}")).andExpect(status().isBadRequest());
    }

    @Test
    void profileWithoutOptionsStillWorks() throws Exception {
        String token = newUser();
        String id = read(postJson(token, "/api/v1/profiles", JAVA_PROFILE).andExpect(status().isCreated()), "$.id");
        getJson(token, "/api/v1/profiles/" + id).andExpect(status().isOk()).andExpect(jsonPath("$.options").doesNotExist());
    }
}
