package dev.jobdragonquest.notifications;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import dev.jobdragonquest.ApiTestSupport;

/** Aviso com onde agir e texto completo (criado pela própria pessoa). */
class NotificationManualTest extends ApiTestSupport {

    @Test
    void avisoGuardaOEnderecoEOTextoCompleto() throws Exception {
        String token = newUser();
        postJson(token, "/api/v1/notifications/manual", """
                {"title":"Segsat: testes online aprovados","company":"Segsat","message":"Ver a mensagem do recrutador",
                 "actionUrl":"https://www.gupy.io/candidaturas","body":"Olá, Thayná! Você foi aprovada nos testes online."}""")
                .andExpect(status().isCreated());
        getJson(token, "/api/v1/notifications?size=10")
                .andExpect(jsonPath("$.items[0].actionUrl").value("https://www.gupy.io/candidaturas"))
                .andExpect(jsonPath("$.items[0].body").value("Olá, Thayná! Você foi aprovada nos testes online."));
    }

    @Test
    void enderecoComEsquemaPerigosoEhRecusado() throws Exception {
        String token = newUser();
        postJson(token, "/api/v1/notifications/manual", """
                {"title":"Teste","message":"Teste","actionUrl":"javascript:alert(1)"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_action_url"));
    }

    @Test
    void textoGrandeDemaisEhRecusado() throws Exception {
        String token = newUser();
        String grande = "x".repeat(20001);
        postJson(token, "/api/v1/notifications/manual", "{\"title\":\"Teste\",\"message\":\"Teste\",\"body\":\"" + grande + "\"}")
                .andExpect(status().isBadRequest());
    }
}
