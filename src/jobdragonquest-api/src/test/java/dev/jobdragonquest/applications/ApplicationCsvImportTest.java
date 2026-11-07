package dev.jobdragonquest.applications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.jobdragonquest.ApiTestSupport;
import tools.jackson.databind.json.JsonMapper;

/** Importação de candidaturas por CSV (issue #114). */
class ApplicationCsvImportTest extends ApiTestSupport {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private String body(String csv) {
        return JSON.writeValueAsString(java.util.Map.of("csv", csv));
    }

    @Test
    void importsSemicolonCsvWithoutDuplicatingAndReportsBadLines() throws Exception {
        String token = newUser();
        String csv = """
                Empresa;Cargo;Link;Plataforma;Data;Etapa;Notas
                Acme;Desenvolvedora Java;https://acme.test/vagas/1;LinkedIn;03/11/2026;entrevista;"Falar com ""RH""\"
                Beta;Dev Python;;Gupy;2026-11-01;;
                ;Sem empresa;;;;;
                Gama;Dev Go;;;31/02/2026;;
                Delta;Dev Rust;;;;inventada;

                """;
        postJson(token, "/api/v1/applications/import", body(csv))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(2))
                .andExpect(jsonPath("$.duplicates").value(0))
                .andExpect(jsonPath("$.errors.length()").value(3))
                .andExpect(jsonPath("$.errors[0].line").value(4))
                .andExpect(jsonPath("$.errors[0].code").value("csv_missing_fields"))
                .andExpect(jsonPath("$.errors[1].code").value("csv_invalid_date"))
                .andExpect(jsonPath("$.errors[2].code").value("csv_invalid_state"));

        getJson(token, "/api/v1/applications?company=acme")
                .andExpect(jsonPath("$.content[0].state").value("INTERVIEW_SCHEDULED"))
                .andExpect(jsonPath("$.content[0].origin").value("IMPORT"))
                .andExpect(jsonPath("$.content[0].appliedAt").value("2026-11-03T00:00:00Z"));

        // O mesmo arquivo de novo: nada duplica.
        postJson(token, "/api/v1/applications/import", body(csv))
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.duplicates").value(2));
        getJson(token, "/api/v1/applications").andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void englishCommaHeaderAndLimits() throws Exception {
        String token = newUser();
        postJson(token, "/api/v1/applications/import", body("company,title,url\nZeta,\"Dev, Backend\",https://zeta.test/1\n"))
                .andExpect(jsonPath("$.created").value(1));
        getJson(token, "/api/v1/applications?company=zeta").andExpect(jsonPath("$.content[0].title").value("Dev, Backend"));

        postJson(token, "/api/v1/applications/import", body("nome,idade\nx,1\n"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("csv_header"));
        StringBuilder big = new StringBuilder("empresa,cargo\n");
        for (int i = 0; i <= ApplicationCsvImporter.MAX_ROWS; i++) {
            big.append("E").append(i).append(",C\n");
        }
        postJson(token, "/api/v1/applications/import", body(big.toString()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("csv_too_many_rows"));
        // Outra conta não vê o que foi importado.
        getJson(newUser(), "/api/v1/applications").andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void parserHandlesQuotesAndLineBreaks() {
        List<List<String>> rows = ApplicationCsvImporter.parse("a,b\r\n\"x\ny\",\"z\"\"w\"\n");
        assertThat(rows).containsExactly(List.of("a", "b"), List.of("x\ny", "z\"w"));
    }
}
