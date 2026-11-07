package dev.jobdragonquest.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import dev.jobdragonquest.applications.ApplicationState;
import dev.jobdragonquest.notifications.NotificationPreferences.Settings;
import dev.jobdragonquest.sources.SourceService;

/** Regras puras dos avisos: antecedência alcançada, horário de silêncio e prioridades padrão. */
class NotificationRulesTest {

    private static final Instant DUE = Instant.parse("2026-11-10T15:00:00Z");

    @Test
    void reachedLeadPicksTheClosestReminderAlreadyDue() {
        List<Integer> leads = List.of(1440, 60);
        assertThat(NotificationGenerator.reachedLead(leads, DUE, DUE.minusSeconds(2 * 86_400))).isNull();
        assertThat(NotificationGenerator.reachedLead(leads, DUE, DUE.minusSeconds(86_400))).isEqualTo(1440);
        assertThat(NotificationGenerator.reachedLead(leads, DUE, DUE.minusSeconds(3_600))).isEqualTo(60);
        assertThat(NotificationGenerator.reachedLead(leads, DUE, DUE.minusSeconds(600))).isEqualTo(60);
        assertThat(NotificationGenerator.reachedLead(null, DUE, DUE)).isNull();
    }

    private static NotificationPreferences at(String utc) {
        return new NotificationPreferences(mock(JdbcTemplate.class), mock(SourceService.class),
                Clock.fixed(Instant.parse(utc), ZoneOffset.UTC));
    }

    @Test
    void quietHoursWorkAcrossMidnightInTheAccountTimeZone() {
        Settings night = new Settings(true, "22:00", "07:00", false, false);
        ZoneId recife = ZoneId.of("America/Recife");
        assertThat(at("2026-11-10T02:00:00Z").isQuiet(night, recife)).as("23h em Recife").isTrue();
        assertThat(at("2026-11-10T09:30:00Z").isQuiet(night, recife)).as("06h30 em Recife").isTrue();
        assertThat(at("2026-11-10T10:00:00Z").isQuiet(night, recife)).as("07h em Recife").isFalse();
        assertThat(at("2026-11-10T15:00:00Z").isQuiet(night, recife)).as("meio-dia").isFalse();
        assertThat(at("2026-11-10T14:30:00Z").isQuiet(new Settings(true, "12:00", "13:00", false, false), recife)).isFalse();
        assertThat(at("2026-11-10T15:30:00Z").isQuiet(new Settings(true, "12:00", "13:00", false, false), recife)).isTrue();
        assertThat(at("2026-11-10T02:00:00Z").isQuiet(new Settings(false, "22:00", "07:00", false, false), recife)).isFalse();
        assertThat(at("2026-11-10T02:00:00Z").isQuiet(new Settings(true, "08:00", "08:00", false, false), recife)).isFalse();
    }

    @Test
    void defaultsFollowPriority() {
        assertThat(NotificationCatalog.priority(NotificationCatalog.stageTopic(ApplicationState.OFFER_RECEIVED)))
                .isEqualTo(NotificationPriority.HIGH);
        assertThat(NotificationCatalog.priority(NotificationCatalog.stageTopic(ApplicationState.RECOMMENDED)))
                .isEqualTo(NotificationPriority.LOW);
        assertThat(NotificationCatalog.defaults("NO_UPDATE").popup()).isFalse();
        assertThat(NotificationCatalog.defaults("TECH_TEST").sound()).isTrue();
        assertThat(NotificationCatalog.defaults("DEADLINE_REMINDER").leadMinutes()).containsExactly(4320, 1440);
        assertThat(NotificationCatalog.isNewJobs("NEW_JOBS:not-a-uuid")).isFalse();
        assertThat(NotificationCatalog.stageOf("STAGE:INVENTADA")).isEmpty();
        assertThat(NotificationCatalog.fixedTopics()).hasSize(19 + 8);
    }
}
