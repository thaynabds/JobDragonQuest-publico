package dev.jobfly.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Num servidor público, só os e-mails da lista criam conta (JOBFLY_ALLOWED_EMAILS). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "jobfly.registration.allowed-emails= Dona@Teste.dev , outra@teste.dev")
class RegistrationAllowlistTest {

    @Autowired
    private MockMvc mvc;

    private org.springframework.test.web.servlet.ResultActions register(String email) throws Exception {
        return mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","password":"senha-forte-de-teste-123","displayName":"X"}""".formatted(email)));
    }

    @Test
    void onlyListedEmailsCanRegister() throws Exception {
        register("dona@teste.dev").andExpect(status().isCreated());
        register("estranho@teste.dev").andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("registration_closed"));
        register("OUTRA@teste.dev").andExpect(status().isCreated());
    }
}
