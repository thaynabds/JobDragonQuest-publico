package dev.jobdragonquest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

/** Base dos testes de integração: sobe a aplicação inteira (Flyway, JPA, segurança) sobre H2 em modo PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class ApiTestSupport {

    protected static final String PASSWORD = "senha-forte-de-teste-123";

    @Autowired
    protected MockMvc mvc;

    /** Cria um usuário novo e devolve o token de acesso. Cada teste usa usuários próprios. */
    protected String newUser() throws Exception {
        String email = "u" + UUID.randomUUID() + "@teste.dev";
        String body = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","displayName":"Pessoa Teste"}""".formatted(email, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    protected ResultActions getJson(String token, String path) throws Exception {
        return mvc.perform(auth(get(path), token));
    }

    protected ResultActions postJson(String token, String path, String json) throws Exception {
        return mvc.perform(auth(post(path), token).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    protected ResultActions putJson(String token, String path, String json) throws Exception {
        return mvc.perform(auth(put(path), token).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    protected ResultActions deleteJson(String token, String path) throws Exception {
        return mvc.perform(auth(delete(path), token));
    }

    protected static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder builder, String token) {
        return token == null ? builder : builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    protected static String read(ResultActions result, String path) throws Exception {
        Object value = JsonPath.read(result.andReturn().getResponse().getContentAsString(), path);
        return String.valueOf(value);
    }

    /** Perfil Java remoto/híbrido em Recife, usado em vários testes. */
    protected static final String JAVA_PROFILE = """
            {"name":"Desenvolvedora Java","seniority":"JUNIOR","minMatchScore":60,
             "desiredTitles":["Desenvolvedora Java"],"locations":["Recife"],"workModes":["REMOTE","HYBRID"],
             "skills":[{"name":"Java","level":4,"years":1.5},{"name":"Spring-Boot","level":3,"years":1},
                       {"name":"PostgreSQL","level":3}],
             "languages":[{"code":"pt","level":"NATIVE"},{"code":"en","level":"B1"}],
             "entries":[{"kind":"PROJECT","title":"Observatório","url":"https://observatorio.grupotp.polyclub.com.br"}]}""";
}
