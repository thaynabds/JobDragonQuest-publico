package dev.jobdragonquest.assist;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import dev.jobdragonquest.ApiTestSupport;

/**
 * Envio automático da extensão: só liga depois do aprendizado (10 envios com um clique confirmados) e com o aviso
 * aceito, e o envio automático não conta para o aprendizado.
 */
class AssistAutoSubmitTest extends ApiTestSupport {

    private static final String ESTADO = "/api/v1/assist/envio-automatico/estado";
    private static final String LIGA = "/api/v1/assist/envio-automatico";
    private static final String ENVIO = "/api/v1/assist/submitted";

    /**
     * Vaga salva e candidatura preparada para ela. Devolve o id da candidatura. Empresa e cargo saem da URL: a API
     * trata mesma empresa e cargo como candidatura repetida (200), então cada vaga do teste precisa ser distinta.
     */
    private String preparada(String token, String url) throws Exception {
        String empresa = "Auto " + url;
        String cargo = "Dev " + url;
        String jobId = read(postJson(token, "/api/v1/jobs", """
                {"source":"MANUAL","url":"%s","company":"%s","title":"%s","workMode":"REMOTE"}"""
                .formatted(url, empresa, cargo)).andExpect(status().isCreated()), "$.id");
        return read(postJson(token, "/api/v1/applications", """
                {"origin":"AUTOMATION","jobId":"%s","state":"PREPARED","company":"%s","title":"%s",
                 "url":"%s"}""".formatted(jobId, empresa, cargo, url)).andExpect(status().isCreated()), "$.id");
    }

    /** Envio com um clique confirmado pela pessoa. */
    private void confirmada(String token, String url) throws Exception {
        preparada(token, url);
        postJson(token, ENVIO, "{\"url\":\"" + url + "\",\"evidence\":\"Enviada com confirmação\"}")
                .andExpect(status().isNoContent());
    }

    private void aprender(String token) throws Exception {
        for (int i = 0; i < EnvioAutomaticoService.LIMITE_APRENDIZADO; i++) {
            confirmada(token, "https://empresa.test/vagas/aprender-" + i);
        }
    }

    @Test
    void comecaDesligadoESemAprendizado() throws Exception {
        String token = newUser();

        postJson(token, ESTADO, "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmados").value(0))
                .andExpect(jsonPath("$.limite").value(EnvioAutomaticoService.LIMITE_APRENDIZADO))
                .andExpect(jsonPath("$.liberado").value(false))
                .andExpect(jsonPath("$.ativo").value(false));
    }

    @Test
    void naoLigaAntesDoAprendizado() throws Exception {
        String token = newUser();

        postJson(token, LIGA, "{\"ativo\":true,\"avisoAceito\":true}").andExpect(status().isConflict());
    }

    @Test
    void ligaSoDepoisDoAprendizadoEComAvisoAceito() throws Exception {
        String token = newUser();
        aprender(token);

        postJson(token, ESTADO, "{}").andExpect(jsonPath("$.liberado").value(true));
        postJson(token, LIGA, "{\"ativo\":true,\"avisoAceito\":false}").andExpect(status().isBadRequest());
        postJson(token, LIGA, "{\"ativo\":true,\"avisoAceito\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(true));
        postJson(token, LIGA, "{\"ativo\":false,\"avisoAceito\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));
    }

    @Test
    void envioAutomaticoRegistraSemContarNoAprendizado() throws Exception {
        String token = newUser();
        aprender(token);
        postJson(token, LIGA, "{\"ativo\":true,\"avisoAceito\":true}").andExpect(status().isOk());
        String url = "https://empresa.test/vagas/automatica";
        String applicationId = preparada(token, url);

        postJson(token, ENVIO, "{\"url\":\"" + url + "\",\"evidence\":\"Enviada pelo automático\",\"automatico\":true}")
                .andExpect(status().isNoContent());

        getJson(token, "/api/v1/applications/" + applicationId)
                .andExpect(jsonPath("$.application.state").value("SUBMITTED"));
        postJson(token, ESTADO, "{}")
                .andExpect(jsonPath("$.confirmados").value(EnvioAutomaticoService.LIMITE_APRENDIZADO));
    }

    @Test
    void envioAutomaticoRecusadoComALigaDesligada() throws Exception {
        String token = newUser();
        String url = "https://empresa.test/vagas/sem-liga";
        String applicationId = preparada(token, url);

        postJson(token, ENVIO, "{\"url\":\"" + url + "\",\"evidence\":\"x\",\"automatico\":true}")
                .andExpect(status().isConflict());

        getJson(token, "/api/v1/applications/" + applicationId)
                .andExpect(jsonPath("$.application.state").value("PREPARED"));
    }
}
