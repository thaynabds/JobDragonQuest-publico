package dev.jobdragonquest.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

import com.jayway.jsonpath.JsonPath;

import dev.jobdragonquest.ApiTestSupport;
import dev.jobdragonquest.sources.JobsCollectedEvent;

/** Critérios de aceite da issue #55: avisos configuráveis por etapa, tipo e fonte, sem repetição e isolados. */
class NotificationIntegrationTest extends ApiTestSupport {

    @Autowired
    private ApplicationEventPublisher publisher;

    private String application(String token, String extra) throws Exception {
        return read(postJson(token, "/api/v1/applications", """
                {"origin":"MANUAL","company":"Acme %s","title":"Dev Java","url":"https://acme.dev/jobs/%s"%s}"""
                .formatted(UUID.randomUUID(), UUID.randomUUID(), extra)).andExpect(status().isCreated()), "$.id");
    }

    private void transition(String token, String id, String json) throws Exception {
        postJson(token, "/api/v1/applications/" + id + "/transitions", json).andExpect(status().isOk());
    }

    private List<String> dueTypes(String token) throws Exception {
        String body = getJson(token, "/api/v1/notifications/due").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> types = JsonPath.read(body, "$[*].type");
        return types;
    }

    private String sourceId(String token) throws Exception {
        String profile = read(postJson(token, "/api/v1/profiles", """
                {"name":"Java","seniority":"JUNIOR","desiredTitles":["Desenvolvedora Java"]}""")
                .andExpect(status().isCreated()), "$.id");
        return read(postJson(token, "/api/v1/sources/bulk", """
                {"profileIds":["%s"],"frequencyMinutes":120,"keywords":[],
                 "sources":[{"name":"Board Acme","kind":"LEVER","identifier":"acme-%s"}]}"""
                .formatted(profile, UUID.randomUUID().toString().substring(0, 8))).andExpect(status().isCreated()),
                "$.created[0].id");
    }

    @Test
    void preferencesListEveryStageTypeAndOwnSourceWithDefaults() throws Exception {
        String token = newUser();
        String source = sourceId(token);
        getJson(token, "/api/v1/notifications/preferences")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferences.length()").value(19 + 8 + 1))
                .andExpect(jsonPath("$.preferences[?(@.topic=='STAGE:INTERVIEW_SCHEDULED')].priority").value(hasItem("HIGH")))
                .andExpect(jsonPath("$.preferences[?(@.topic=='STAGE:INTERVIEW_SCHEDULED')].preference.system").value(hasItem(true)))
                .andExpect(jsonPath("$.preferences[?(@.topic=='NO_UPDATE')].preference.popup").value(hasItem(false)))
                .andExpect(jsonPath("$.preferences[?(@.topic=='NO_UPDATE')].preference.staleDays").value(hasItem(14)))
                .andExpect(jsonPath("$.preferences[?(@.topic=='INTERVIEW_REMINDER')].preference.leadMinutes[0]").value(hasItem(1440)))
                .andExpect(jsonPath("$.preferences[?(@.topic=='NEW_JOBS:" + source + "')].label").value(hasItem("Board Acme")))
                .andExpect(jsonPath("$.settings.quietEnabled").value(false))
                .andExpect(jsonPath("$.timeZone").isNotEmpty());
    }

    @Test
    void rejectsInvalidPreferencesAndForeignSources() throws Exception {
        String token = newUser();
        String foreign = sourceId(newUser());
        String own = sourceId(token);
        putJson(token, "/api/v1/notifications/preferences", """
                {"preferences":[{"topic":"NEW_JOBS:%s","enabled":true,"popup":true,"system":true,"sound":true}]}"""
                .formatted(foreign)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid_topic"));
        putJson(token, "/api/v1/notifications/preferences", """
                {"preferences":[{"topic":"../../etc","enabled":true,"popup":true,"system":true,"sound":true}]}""")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid_topic"));
        putJson(token, "/api/v1/notifications/preferences", """
                {"preferences":[{"topic":"INTERVIEW_REMINDER","enabled":true,"popup":true,"system":true,"sound":true,
                 "leadMinutes":[1]}]}""")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid_lead"));
        putJson(token, "/api/v1/notifications/preferences", """
                {"preferences":[{"topic":"TECH_TEST","enabled":true,"popup":true,"system":true,"sound":true,"minScore":50}]}""")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid_min_score"));
        putJson(token, "/api/v1/notifications/preferences", """
                {"preferences":[{"topic":"NO_UPDATE","enabled":true,"popup":true,"system":true,"sound":true,"staleDays":400}]}""")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid_stale_days"));
        putJson(token, "/api/v1/notifications/preferences", """
                {"settings":{"quietEnabled":true,"quietStart":"25:00","quietEnd":"08:00","quietAllowHigh":true,"hideDetails":false}}""")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid_quiet_hours"));

        putJson(token, "/api/v1/notifications/preferences", """
                {"settings":{"quietEnabled":true,"quietStart":"22:00","quietEnd":"07:30","quietAllowHigh":false,"hideDetails":true},
                 "preferences":[{"topic":"NEW_JOBS:%s","enabled":true,"popup":true,"system":false,"sound":false,"minScore":70},
                                {"topic":"INTERVIEW_REMINDER","enabled":true,"popup":true,"system":true,"sound":true,
                                 "leadMinutes":[30,2880,30]}]}""".formatted(own))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settings.hideDetails").value(true))
                .andExpect(jsonPath("$.settings.quietEnd").value("07:30"))
                .andExpect(jsonPath("$.preferences[?(@.topic=='NEW_JOBS:" + own + "')].preference.minScore").value(hasItem(70)))
                .andExpect(jsonPath("$.preferences[?(@.topic=='INTERVIEW_REMINDER')].preference.leadMinutes.length()").value(hasItem(2)));
    }

    @Test
    void stageChangesMadeByOthersNotifyOnceAndManualChangesDoNot() throws Exception {
        String token = newUser();
        String app = application(token, "");
        transition(token, app, """
                {"toState":"SUBMITTED","source":"MANUAL","evidence":"Enviei pelo site"}""");
        assertThat(dueTypes(token)).isEmpty();

        transition(token, app, """
                {"toState":"INTERVIEW_SCHEDULED","source":"EMAIL","confidence":0.95,"evidence":"Convite por e-mail"}""");
        getJson(token, "/api/v1/notifications/due")
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("STAGE_CHANGED"))
                .andExpect(jsonPath("$[0].state").value("INTERVIEW_SCHEDULED"))
                .andExpect(jsonPath("$[0].priority").value("HIGH"))
                .andExpect(jsonPath("$[0].popup").value(true))
                .andExpect(jsonPath("$[0].system").value(true))
                .andExpect(jsonPath("$[0].message").value(org.hamcrest.Matchers.containsString("Entrevista agendada")));
        // Consultar de novo não repete o aviso.
        assertThat(dueTypes(token)).hasSize(1);
        getJson(token, "/api/v1/notifications").andExpect(jsonPath("$.unreadCount").value(1));
    }

    @Test
    void disabledStageDoesNotNotify() throws Exception {
        String token = newUser();
        putJson(token, "/api/v1/notifications/preferences", """
                {"preferences":[{"topic":"STAGE:SCREENING","enabled":false,"popup":true,"system":true,"sound":true}]}""")
                .andExpect(status().isOk());
        String app = application(token, ",\"state\":\"SUBMITTED\"");
        transition(token, app, """
                {"toState":"SCREENING","source":"EMAIL","confidence":0.9,"evidence":"Em triagem"}""");
        assertThat(dueTypes(token)).isEmpty();
    }

    @Test
    void pendingItemsAndRemindersBecomeNotificationsWithoutDuplicates() throws Exception {
        String token = newUser();
        application(token, ",\"state\":\"AWAITING_USER\",\"nextAction\":\"Enviar portfólio\"");
        String interview = application(token, ",\"state\":\"SUBMITTED\"");
        String due = Instant.now().plus(30, ChronoUnit.MINUTES).toString();
        transition(token, interview, """
                {"toState":"INTERVIEW_SCHEDULED","source":"MANUAL","evidence":"Marcada","nextDueAt":"%s"}""".formatted(due));
        String test = application(token, ",\"state\":\"SUBMITTED\"");
        transition(token, test, """
                {"toState":"TECH_TEST_PENDING","source":"MANUAL","evidence":"Recebi o teste","nextAction":"Entregar o desafio"}""");

        List<String> types = dueTypes(token);
        assertThat(types).containsExactlyInAnyOrder("ACTION_REQUIRED", "INTERVIEW_REMINDER", "TECH_TEST");
        assertThat(dueTypes(token)).hasSize(3);
        getJson(token, "/api/v1/notifications/due")
                .andExpect(jsonPath("$[?(@.type=='INTERVIEW_REMINDER')].leadMinutes").value(hasItem(60)))
                .andExpect(jsonPath("$[?(@.type=='ACTION_REQUIRED')].detail").value(hasItem("Enviar portfólio")));
    }

    @Test
    void deliveryReadSnoozeAndReadAll() throws Exception {
        String token = newUser();
        String first = read(postJson(token, "/api/v1/notifications/test", "{}").andExpect(status().isOk()), "$.id");
        String second = read(postJson(token, "/api/v1/notifications/test", "{}").andExpect(status().isOk()), "$.id");
        assertThat(dueTypes(token)).containsExactly("TEST", "TEST");

        postJson(token, "/api/v1/notifications/delivered", "{\"ids\":[\"%s\"]}".formatted(first))
                .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(1));
        assertThat(dueTypes(token)).hasSize(1);

        postJson(token, "/api/v1/notifications/" + second + "/snooze", "{\"minutes\":30}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.snoozedUntil").isNotEmpty());
        assertThat(dueTypes(token)).as("adiado sai da fila até o prazo").isEmpty();
        postJson(token, "/api/v1/notifications/" + second + "/snooze", "{\"minutes\":1}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid_snooze"));

        postJson(token, "/api/v1/notifications/" + first + "/read", "{}").andExpect(status().isOk())
                .andExpect(jsonPath("$.readAt").isNotEmpty());
        getJson(token, "/api/v1/notifications?unreadOnly=true").andExpect(jsonPath("$.items.length()").value(1));
        postJson(token, "/api/v1/notifications/read-all", "{}").andExpect(jsonPath("$.count").value(1));
        getJson(token, "/api/v1/notifications").andExpect(jsonPath("$.unreadCount").value(0))
                .andExpect(jsonPath("$.totalItems").value(2));
        getJson(token, "/api/v1/notifications?size=500").andExpect(status().isBadRequest());
        postJson(token, "/api/v1/notifications/delivered", "{\"ids\":[]}").andExpect(status().isBadRequest());
    }

    @Test
    void quietHoursKeepNotificationInTheBellOnly() throws Exception {
        String token = newUser();
        putJson(token, "/api/v1/notifications/preferences", """
                {"settings":{"quietEnabled":true,"quietStart":"00:00","quietEnd":"23:59","quietAllowHigh":false,"hideDetails":false}}""")
                .andExpect(status().isOk());
        String app = application(token, ",\"state\":\"SUBMITTED\"");
        transition(token, app, """
                {"toState":"OFFER_RECEIVED","source":"EMAIL","confidence":0.95,"evidence":"Proposta"}""");
        assertThat(dueTypes(token)).as("sem canal visível, nada a entregar").isEmpty();
        getJson(token, "/api/v1/notifications")
                .andExpect(jsonPath("$.items[0].quiet").value(true))
                .andExpect(jsonPath("$.items[0].popup").value(false))
                .andExpect(jsonPath("$.unreadCount").value(1));

        // Com a exceção para prioridade alta, a proposta (alta) avisa mesmo no silêncio.
        putJson(token, "/api/v1/notifications/preferences", """
                {"settings":{"quietEnabled":true,"quietStart":"00:00","quietEnd":"23:59","quietAllowHigh":true,"hideDetails":false}}""")
                .andExpect(status().isOk());
        transition(token, app, """
                {"toState":"APPROVED","source":"EMAIL","confidence":0.95,"evidence":"Aprovada"}""");
        getJson(token, "/api/v1/notifications/due").andExpect(jsonPath("$[0].state").value("APPROVED"))
                .andExpect(jsonPath("$[0].quiet").value(false));
    }

    @Test
    void newJobsRespectMinimumScorePerSource() throws Exception {
        String token = newUser();
        String source = sourceId(token);
        String body = getJson(token, "/api/v1/me").andReturn().getResponse().getContentAsString();
        UUID user = UUID.fromString(JsonPath.read(body, "$.id"));
        putJson(token, "/api/v1/notifications/preferences", """
                {"preferences":[{"topic":"NEW_JOBS:%s","enabled":true,"popup":true,"system":false,"sound":false,"minScore":70}]}"""
                .formatted(source)).andExpect(status().isOk());

        publisher.publishEvent(new JobsCollectedEvent(user, UUID.fromString(source), "Board Acme", Arrays.asList(85, 40, null),
                UUID.randomUUID()));
        publisher.publishEvent(new JobsCollectedEvent(user, UUID.fromString(source), "Board Acme", Arrays.asList(30, null),
                UUID.randomUUID()));
        getJson(token, "/api/v1/notifications/due")
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("NEW_JOBS"))
                .andExpect(jsonPath("$[0].itemCount").value(1))
                .andExpect(jsonPath("$[0].message").value("1 vaga nova em Board Acme com nota a partir de 70"));
    }

    @Test
    void notificationsAreIsolatedBetweenUsers() throws Exception {
        String owner = newUser();
        String intruder = newUser();
        String id = read(postJson(owner, "/api/v1/notifications/test", "{}"), "$.id");

        getJson(intruder, "/api/v1/notifications").andExpect(jsonPath("$.items[*].id").value(not(hasItem(id))));
        postJson(intruder, "/api/v1/notifications/" + id + "/read", "{}").andExpect(status().isNotFound());
        postJson(intruder, "/api/v1/notifications/" + id + "/snooze", "{\"minutes\":10}").andExpect(status().isNotFound());
        postJson(intruder, "/api/v1/notifications/delivered", "{\"ids\":[\"%s\"]}".formatted(id))
                .andExpect(jsonPath("$.count").value(0));
        postJson(intruder, "/api/v1/notifications/read-all", "{}").andExpect(jsonPath("$.count").value(0));
        getJson(owner, "/api/v1/notifications").andExpect(jsonPath("$.unreadCount").value(1));
        getJson(null, "/api/v1/notifications").andExpect(status().isUnauthorized());
    }
}
