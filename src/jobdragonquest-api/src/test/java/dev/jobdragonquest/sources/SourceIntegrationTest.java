package dev.jobdragonquest.sources;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import dev.jobdragonquest.ApiTestSupport;

/** Critérios de aceite da issue #9: fontes vigiadas em lote para vários perfis. */
class SourceIntegrationTest extends ApiTestSupport {

    private String profile(String token, String name) throws Exception {
        return read(postJson(token, "/api/v1/profiles", """
                {"name":"%s","seniority":"JUNIOR","desiredTitles":["Desenvolvedora Java"]}""".formatted(name))
                .andExpect(status().isCreated()), "$.id");
    }

    private static String bulk(String profiles, String sources) {
        return """
                {"profileIds":[%s],"frequencyMinutes":120,"keywords":["Java","Estágio"],"sources":[%s]}"""
                .formatted(profiles, sources);
    }

    private static final String THREE_SOURCES = """
            {"name":"Nubank","kind":"GREENHOUSE","identifier":"nubank"},
            {"name":"Vagas Java no LinkedIn","kind":"LINKEDIN","url":"https://www.linkedin.com/jobs/search/?keywords=java&location=Recife"},
            {"name":"#VagasTI","kind":"INSTAGRAM","url":"https://www.instagram.com/explore/tags/vagasti/"}""";

    @Test
    void createsManySourcesLinkedToManyProfilesAndReportsDuplicates() throws Exception {
        String token = newUser();
        String java = profile(token, "Java");
        String intern = profile(token, "Estágio");
        String ids = "\"%s\",\"%s\"".formatted(java, intern);

        postJson(token, "/api/v1/sources/bulk", bulk(ids, THREE_SOURCES))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.created.length()").value(3))
                .andExpect(jsonPath("$.duplicates").isEmpty())
                .andExpect(jsonPath("$.created[0].profileIds", containsInAnyOrder(java, intern)))
                .andExpect(jsonPath("$.created[0].collectionMode").value("AUTOMATIC"))
                .andExpect(jsonPath("$.created[0].openUrl").value("https://boards.greenhouse.io/nubank"))
                .andExpect(jsonPath("$.created[0].keywords", containsInAnyOrder("Java", "Estágio")))
                .andExpect(jsonPath("$.created[1].collectionMode").value("ASSISTED"))
                .andExpect(jsonPath("$.created[2].frequencyMinutes").value(120))
                .andExpect(jsonPath("$.created[2].due").value(true));

        getJson(token, "/api/v1/sources?profileId=" + intern).andExpect(jsonPath("$.length()").value(3));

        // O mesmo board com outra caixa e a mesma busca com rastreio são duplicadas. Também duplica dentro do lote.
        postJson(token, "/api/v1/sources/bulk", bulk(ids, """
                {"name":"Nubank de novo","kind":"GREENHOUSE","identifier":"NUBANK"},
                {"name":"Busca repetida","kind":"LINKEDIN","url":"https://linkedin.com/jobs/search?location=Recife&keywords=java&trk=abc"},
                {"name":"Lever A","kind":"LEVER","identifier":"empresa-x"},
                {"name":"Lever A repetida","kind":"LEVER","identifier":"empresa-x"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.created.length()").value(1))
                .andExpect(jsonPath("$.duplicates.length()").value(3))
                .andExpect(jsonPath("$.duplicates[*].index", containsInAnyOrder(0, 1, 3)));

        getJson(token, "/api/v1/sources").andExpect(jsonPath("$.length()").value(4));
    }

    @Test
    void invalidItemRejectsTheWholeBatch() throws Exception {
        String token = newUser();
        String java = profile(token, "Java");
        String foreign = profile(newUser(), "Alheio");

        postJson(token, "/api/v1/sources/bulk", bulk("\"%s\"".formatted(java), """
                {"name":"Ok","kind":"LEVER","identifier":"ok"},
                {"name":"Ataque","kind":"GREENHOUSE","identifier":".."}"""))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid_identifier"));
        postJson(token, "/api/v1/sources/bulk", bulk("\"%s\"".formatted(java), """
                {"name":"Sem URL","kind":"LINKEDIN"}"""))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("url_required"));
        postJson(token, "/api/v1/sources/bulk", bulk("\"%s\"".formatted(java), """
                {"name":"XSS","kind":"FACEBOOK","url":"javascript:alert(1)"}"""))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid_url"));
        postJson(token, "/api/v1/sources/bulk", """
                {"frequencyMinutes":5,"sources":[{"name":"x","kind":"LEVER","identifier":"x"}]}""")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("validation_failed"));
        postJson(token, "/api/v1/sources/bulk", bulk("\"%s\",\"%s\"".formatted(java, foreign), THREE_SOURCES))
                .andExpect(status().isNotFound());

        getJson(token, "/api/v1/sources").andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void bulkUpdatePausesChangesFrequencyAndProfiles() throws Exception {
        String token = newUser();
        String java = profile(token, "Java");
        String intern = profile(token, "Estágio");
        var created = postJson(token, "/api/v1/sources/bulk", bulk("\"%s\"".formatted(java), THREE_SOURCES));
        String a = read(created, "$.created[0].id");
        String b = read(created, "$.created[1].id");

        postJson(token, "/api/v1/sources/bulk-update", """
                {"sourceIds":["%s","%s"],"enabled":false,"frequencyMinutes":60,"addProfileIds":["%s"]}"""
                .formatted(a, b, intern))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].enabled").value(false))
                .andExpect(jsonPath("$[0].due").value(false))
                .andExpect(jsonPath("$[0].frequencyMinutes").value(60))
                .andExpect(jsonPath("$[0].profileIds", containsInAnyOrder(java, intern)));

        postJson(token, "/api/v1/sources/bulk-update", """
                {"sourceIds":["%s"],"removeProfileIds":["%s"],"enabled":true}""".formatted(a, java))
                .andExpect(jsonPath("$[0].profileIds", not(hasItem(java))))
                .andExpect(jsonPath("$[0].enabled").value(true));

        postJson(token, "/api/v1/sources/bulk-update", """
                {"sourceIds":["%s"],"setProfileIds":["%s"],"addProfileIds":["%s"]}""".formatted(a, java, intern))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("conflicting_profile_change"));
        postJson(token, "/api/v1/sources/bulk-update", """
                {"sourceIds":["%s"]}""".formatted(a))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("empty_bulk_update"));
        postJson(token, "/api/v1/sources/bulk-update", """
                {"sourceIds":["%s","00000000-0000-0000-0000-000000000000"],"enabled":false}""".formatted(a))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateWithVersionAndDuplicateProtection() throws Exception {
        String token = newUser();
        var created = postJson(token, "/api/v1/sources/bulk", bulk("", THREE_SOURCES));
        String id = read(created, "$.created[0].id");

        putJson(token, "/api/v1/sources/" + id, """
                {"name":"Nubank Brasil","identifier":"nubank","frequencyMinutes":30,"keywords":["Backend"],
                 "profileIds":[],"enabled":true,"version":0}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Nubank Brasil"))
                .andExpect(jsonPath("$.keywords[0]").value("Backend"))
                .andExpect(jsonPath("$.version").value(1));
        putJson(token, "/api/v1/sources/" + id, """
                {"name":"x","identifier":"nubank","frequencyMinutes":30,"enabled":true,"version":0}""")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("stale_version"));

        String lever = read(postJson(token, "/api/v1/sources/bulk", bulk("", """
                {"name":"Lever","kind":"GREENHOUSE","identifier":"outra"}""")), "$.created[0].id");
        putJson(token, "/api/v1/sources/" + lever, """
                {"name":"Lever","identifier":"NuBank","frequencyMinutes":30,"enabled":true,"version":0}""")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("source_duplicate"));
    }

    @Test
    void sourcesAreIsolatedAndFollowProfileDeletion() throws Exception {
        String owner = newUser();
        String intruder = newUser();
        String java = profile(owner, "Java");
        String id = read(postJson(owner, "/api/v1/sources/bulk", bulk("\"%s\"".formatted(java), THREE_SOURCES)),
                "$.created[0].id");

        getJson(intruder, "/api/v1/sources/" + id).andExpect(status().isNotFound());
        deleteJson(intruder, "/api/v1/sources/" + id).andExpect(status().isNotFound());
        getJson(intruder, "/api/v1/sources").andExpect(jsonPath("$.length()").value(0));
        postJson(intruder, "/api/v1/sources/bulk-update", """
                {"sourceIds":["%s"],"enabled":false}""".formatted(id)).andExpect(status().isNotFound());

        deleteJson(owner, "/api/v1/profiles/" + java).andExpect(status().isNoContent());
        getJson(owner, "/api/v1/sources/" + id).andExpect(jsonPath("$.profileIds").isEmpty());

        deleteJson(owner, "/api/v1/sources/" + id).andExpect(status().isNoContent());
        getJson(owner, "/api/v1/sources/" + id).andExpect(status().isNotFound());
    }

    @Test
    void catalogExplainsHowEachKindIsWatched() throws Exception {
        String token = newUser();
        getJson(token, "/api/v1/sources/catalog").andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(SourceKind.values().length))
                .andExpect(jsonPath("$[?(@.kind=='GREENHOUSE')].mode").value(hasItem("AUTOMATIC")))
                .andExpect(jsonPath("$[?(@.kind=='LINKEDIN')].mode").value(hasItem("ASSISTED")))
                .andExpect(jsonPath("$[?(@.kind=='INSTAGRAM')].needsIdentifier").value(hasItem(false)));
    }
}
