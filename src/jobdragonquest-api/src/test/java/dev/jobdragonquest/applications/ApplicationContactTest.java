package dev.jobdragonquest.applications;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import dev.jobdragonquest.ApiTestSupport;

/** Contatos por candidatura (issue #108). */
class ApplicationContactTest extends ApiTestSupport {

    private String create(String token) throws Exception {
        return read(postJson(token, "/api/v1/applications", """
                {"origin":"MANUAL","platform":"LinkedIn","company":"Contato SA","title":"Dev Java"}""")
                .andExpect(status().isCreated()), "$.id");
    }

    @Test
    void contactMovesFreshApplicationToRecruiterContactAndStaysPrivate() throws Exception {
        String token = newUser();
        String app = create(token);

        // Só guardar o contato não muda a etapa.
        postJson(token, "/api/v1/applications/" + app + "/contacts", """
                {"role":"HIRING_MANAGER","name":"Gestora Fictícia","position":"Tech Lead"}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contactedAt").doesNotExist());
        getJson(token, "/api/v1/applications/" + app).andExpect(jsonPath("$.application.state").value("SUBMITTED"));

        // Conversa registrada: a candidatura vai para contato com recrutador, com o contato como evidência.
        String contact = read(postJson(token, "/api/v1/applications/" + app + "/contacts", """
                {"role":"RECRUITER","name":"Recrutadora Fictícia","email":"recruta@empresa.test",
                 "linkedinUrl":"https://www.linkedin.com/in/recrutadora-ficticia","contacted":true}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contactedAt").exists()), "$.id");
        getJson(token, "/api/v1/applications/" + app)
                .andExpect(jsonPath("$.application.state").value("RECRUITER_CONTACT"))
                .andExpect(jsonPath("$.events[?(@.toState == 'RECRUITER_CONTACT')].evidence")
                        .value(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("Recrutadora Fictícia"))));
        getJson(token, "/api/v1/applications/" + app + "/contacts").andExpect(jsonPath("$.length()").value(2));

        // Outra conta não lê, não grava e não apaga.
        String intruder = newUser();
        getJson(intruder, "/api/v1/applications/" + app + "/contacts").andExpect(status().isNotFound());
        postJson(intruder, "/api/v1/applications/" + app + "/contacts", """
                {"role":"OTHER","name":"Intrusa"}""").andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/applications/" + app + "/contacts/" + contact).header("Authorization", "Bearer " + intruder))
                .andExpect(status().isNotFound());

        mvc.perform(delete("/api/v1/applications/" + app + "/contacts/" + contact).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        getJson(token, "/api/v1/applications/" + app + "/contacts").andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void laterStageDoesNotGoBackAndInputIsValidated() throws Exception {
        String token = newUser();
        String app = create(token);
        postJson(token, "/api/v1/applications/" + app + "/transitions", """
                {"toState":"INTERVIEW_SCHEDULED","source":"MANUAL","evidence":"Convite"}""").andExpect(status().isOk());
        postJson(token, "/api/v1/applications/" + app + "/contacts", """
                {"role":"INTERVIEWER","name":"Entrevistador","contacted":true}""").andExpect(status().isCreated());
        getJson(token, "/api/v1/applications/" + app).andExpect(jsonPath("$.application.state").value("INTERVIEW_SCHEDULED"));

        postJson(token, "/api/v1/applications/" + app + "/contacts", """
                {"role":"RECRUITER","name":"X","email":"sem-arroba"}""")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid_email"));
        postJson(token, "/api/v1/applications/" + app + "/contacts", """
                {"role":"RECRUITER","name":"X","linkedinUrl":"javascript:alert(1)"}""").andExpect(status().isBadRequest());
        postJson(token, "/api/v1/applications/" + app + "/contacts", """
                {"role":"RECRUITER","name":" "}""").andExpect(status().isBadRequest());
        postJson(token, "/api/v1/applications/" + app + "/contacts", """
                {"role":"CEO","name":"X"}""").andExpect(status().isBadRequest());
    }

    @Test
    void limitsContactsPerApplication() throws Exception {
        String token = newUser();
        String app = create(token);
        for (int i = 0; i < ApplicationContactService.MAX_CONTACTS; i++) {
            postJson(token, "/api/v1/applications/" + app + "/contacts", """
                    {"role":"OTHER","name":"Pessoa %d"}""".formatted(i)).andExpect(status().isCreated());
        }
        postJson(token, "/api/v1/applications/" + app + "/contacts", """
                {"role":"OTHER","name":"Uma a mais"}""")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("contact_limit"));
    }
}
