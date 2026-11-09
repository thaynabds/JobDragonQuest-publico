package dev.jobdragonquest.contests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import dev.jobdragonquest.ApiTestSupport;
import dev.jobdragonquest.common.error.DomainException;
import dev.jobdragonquest.jobs.link.SafeWebFetcher;

/** Concursos (docs/concursos.md): etapas, páginas vigiadas, menções e documento não lido. */
class ContestTest extends ApiTestSupport {

    private static final String PAGE = "https://93.184.216.34/concurso";
    private static final String BODY = """
            {"name":"Concurso Teste","registration":"778899","searchName":"Maria da Silva Teste",
             "stages":[{"title":"Inscrição","status":"OPEN","deadline":"2030-01-01"}]}""";

    @MockitoBean
    SafeWebFetcher fetcher;

    private String contest(String token) throws Exception {
        String id = read(postJson(token, "/api/v1/contests", BODY).andExpect(status().isCreated()), "$.id");
        postJson(token, "/api/v1/contests/" + id + "/watches", "{\"url\":\"" + PAGE + "\"}").andExpect(status().isCreated());
        return id;
    }

    private void serves(String html) {
        when(fetcher.fetchBytes(any(URI.class), anyInt())).thenAnswer(call -> {
            URI uri = call.getArgument(0);
            if (uri.getPath().equals("/robots.txt")) {
                throw DomainException.badRequest("link_unreadable", "sem robots");
            }
            return html.getBytes(StandardCharsets.UTF_8);
        });
    }

    @Test
    void numeroDeInscricaoGeraMencaoDeConfiancaAltaUmaVezSo() throws Exception {
        String token = newUser();
        String id = contest(token);
        serves("<table><tr><td>778899</td><td>Convocada para posse</td></tr></table>");
        postJson(token, "/api/v1/contests/" + id + "/scan", "{}").andExpect(jsonPath("$.matches").value(1));
        postJson(token, "/api/v1/contests/" + id + "/scan", "{}").andExpect(jsonPath("$.matches").value(0));
        getJson(token, "/api/v1/contests/" + id)
                .andExpect(jsonPath("$.matches.length()").value(1))
                .andExpect(jsonPath("$.matches[0].confidence").value("HIGH"));
        getJson(token, "/api/v1/notifications").andExpect(jsonPath("$..message").value(hasItem(containsString("Possível menção"))));
    }

    @Test
    void nomeSemAcentoGeraConfiancaMediaEOutroNomeNao() throws Exception {
        String token = newUser();
        String id = contest(token);
        serves("<p>Lista: MARÍA DA SILVA TESTE, classificada</p><p>Maria da Silva Testes Outra</p>");
        postJson(token, "/api/v1/contests/" + id + "/scan", "{}").andExpect(jsonPath("$.matches").value(1));
        getJson(token, "/api/v1/contests/" + id).andExpect(jsonPath("$.matches[0].confidence").value("MEDIUM"));
    }

    @Test
    void paginaIlegivelAvisaQueNaoFoiLida() throws Exception {
        String token = newUser();
        String id = contest(token);
        when(fetcher.fetchBytes(any(URI.class), anyInt())).thenThrow(DomainException.badRequest("link_unreadable", "fora do ar"));
        postJson(token, "/api/v1/contests/" + id + "/scan", "{}").andExpect(jsonPath("$.unread").value(1));
        getJson(token, "/api/v1/notifications").andExpect(jsonPath("$..message").value(hasItem(containsString("não lido"))));
    }

    @Test
    void enderecoInternoEhRecusado() throws Exception {
        String token = newUser();
        String id = read(postJson(token, "/api/v1/contests", BODY).andExpect(status().isCreated()), "$.id");
        postJson(token, "/api/v1/contests/" + id + "/watches", "{\"url\":\"http://127.0.0.1/x\"}").andExpect(status().is4xxClientError());
    }

    @Test
    void outraContaRecebe404() throws Exception {
        String id = contest(newUser());
        String intruder = newUser();
        getJson(intruder, "/api/v1/contests/" + id).andExpect(status().isNotFound());
        postJson(intruder, "/api/v1/contests/" + id + "/scan", "{}").andExpect(status().isNotFound());
    }

    @Test
    void robotsRespeitadoELinksPdfResolvidos() {
        assertThat(RobotsRules.allows("User-agent: *\nDisallow: /privado", "/privado/a")).isFalse();
        assertThat(RobotsRules.allows("User-agent: outro\nDisallow: /", "/a")).isTrue();
        assertThat(ContestScanner.pdfLinks(URI.create("https://a.test/x/"), "<a href='c.pdf'>e</a><a href=\"javascript:a.pdf\">"))
                .containsExactly(URI.create("https://a.test/x/c.pdf"));
    }
}
