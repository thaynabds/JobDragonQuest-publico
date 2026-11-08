package dev.jobdragonquest.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.jobdragonquest.ApiTestSupport;

/**
 * Issue #159, RNF07: leitura da API em até 300 ms no p95 com carga de um usuário. Mede as rotas de leitura mais usadas
 * pelo painel com 60 candidaturas e 3 perfis cadastrados, 200 leituras por rota depois do aquecimento, com a aplicação
 * inteira (segurança, JPA e banco em memória). O resultado vai para target/desempenho-p95.txt e para o log do teste.
 */
class DesempenhoP95Test extends ApiTestSupport {

    private static final int WARMUP = 30;
    private static final int SAMPLES = 200;
    private static final double LIMIT_MS = 300;

    static double p95(double[] values) {
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        return sorted[(int) Math.ceil(0.95 * sorted.length) - 1];
    }

    @Test
    void leiturasPrincipaisFicamAbaixoDe300msNoP95() throws Exception {
        String token = newUser();
        for (int i = 0; i < 3; i++) {
            postJson(token, "/api/v1/profiles", JAVA_PROFILE.replace("Desenvolvedora Java\",\"seniority", "Perfil " + i + "\",\"seniority"))
                    .andExpect(status().isCreated());
        }
        for (int i = 0; i < 60; i++) {
            postJson(token, "/api/v1/applications", """
                    {"origin":"MANUAL","platform":"LinkedIn","company":"Empresa %d","title":"Desenvolvedora Java %d"}""".formatted(i, i))
                    .andExpect(status().isCreated());
        }
        List<String> routes = List.of("/api/v1/applications?size=20", "/api/v1/applications/board", "/api/v1/profiles",
                "/api/v1/dashboard/summary", "/api/v1/dashboard/pending");
        Map<String, Double> result = new LinkedHashMap<>();
        for (String route : routes) {
            for (int i = 0; i < WARMUP; i++) {
                getJson(token, route).andExpect(status().isOk());
            }
            double[] millis = new double[SAMPLES];
            for (int i = 0; i < SAMPLES; i++) {
                long start = System.nanoTime();
                getJson(token, route).andExpect(status().isOk());
                millis[i] = (System.nanoTime() - start) / 1_000_000.0;
            }
            result.put(route, p95(millis));
        }
        List<String> lines = new ArrayList<>();
        result.forEach((route, value) -> lines.add("%-32s p95 = %6.1f ms".formatted(route, value)));
        String report = String.join("\n", lines);
        System.out.println("Desempenho (RNF07, limite " + LIMIT_MS + " ms):\n" + report);
        try {
            Files.writeString(Path.of("target", "desempenho-p95.txt"), report + "\n");
        } catch (IOException ignored) {
            // O relatório em arquivo é só conveniência; a asserção abaixo é o que vale.
        }
        assertThat(result).allSatisfy((route, value) -> assertThat(value).as(route).isLessThan(LIMIT_MS));
    }

    @Test
    void p95PegaOValorCerto() {
        double[] values = new double[100];
        for (int i = 0; i < 100; i++) {
            values[i] = i + 1;
        }
        assertThat(p95(values)).isEqualTo(95.0);
    }
}
