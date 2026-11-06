package dev.jobfly.notifications;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.jobfly.common.error.DomainException;
import dev.jobfly.sources.SourceDtos.SourceResponse;
import dev.jobfly.sources.SourceService;

/**
 * Preferências de aviso de cada pessoa. Sem linha gravada, vale o padrão do {@link NotificationCatalog}. O fuso do
 * horário de silêncio é o fuso da conta.
 */
@Service
public class NotificationPreferences {

    static final int MAX_LEADS = 5;
    static final int MIN_LEAD_MINUTES = 5;
    static final int MAX_LEAD_MINUTES = 20_160;
    static final int MAX_STALE_DAYS = 90;
    static final int MAX_PREFERENCES_PER_REQUEST = 300;
    private static final Pattern TIME = Pattern.compile("([01]\\d|2[0-3]):[0-5]\\d");

    private final JdbcTemplate jdbc;
    private final SourceService sources;
    private final Clock clock;

    NotificationPreferences(JdbcTemplate jdbc, SourceService sources, Clock clock) {
        this.jdbc = jdbc;
        this.sources = sources;
        this.clock = clock;
    }

    /** Preferência de um tópico. Listas e números só valem nos tópicos que os aceitam (ver o catálogo). */
    public record Preference(String topic, boolean enabled, boolean popup, boolean system, boolean sound,
            List<Integer> leadMinutes, Integer minScore, Integer staleDays) {

        public Preference {
            leadMinutes = leadMinutes == null ? null : List.copyOf(leadMinutes);
        }
    }

    /** Horário de silêncio (HH:mm no fuso da conta) e privacidade dos avisos do sistema. */
    public record Settings(boolean quietEnabled, String quietStart, String quietEnd, boolean quietAllowHigh,
            boolean hideDetails) {

        static final Settings DEFAULT = new Settings(false, "22:00", "08:00", true, false);
    }

    public enum Group {
        STAGE,
        PENDING,
        SOURCE
    }

    /** Linha da tela de configurações: a preferência mais o que o painel precisa para desenhar o controle. */
    public record PreferenceView(String topic, Group group, String key, String label, NotificationPriority priority,
            boolean supportsLead, boolean supportsStaleDays, boolean supportsMinScore, Preference preference) {
    }

    public record PreferencesView(Settings settings, String timeZone, List<PreferenceView> preferences) {
    }

    public record UpdateRequest(Settings settings, List<Preference> preferences) {
    }

    @Transactional(readOnly = true)
    public PreferencesView view(UUID userId) {
        Map<String, Preference> stored = stored(userId);
        List<PreferenceView> rows = new ArrayList<>();
        for (String topic : NotificationCatalog.fixedTopics()) {
            boolean stage = topic.startsWith(NotificationCatalog.STAGE_PREFIX);
            String key = stage ? topic.substring(NotificationCatalog.STAGE_PREFIX.length()) : topic;
            rows.add(row(topic, stage ? Group.STAGE : Group.PENDING, key, null, stored));
        }
        for (SourceResponse source : ownSources(userId)) {
            String topic = NotificationCatalog.newJobsTopic(source.id());
            rows.add(row(topic, Group.SOURCE, source.id().toString(), source.name(), stored));
        }
        return new PreferencesView(settings(userId), timeZone(userId), List.copyOf(rows));
    }

    /** Preferência efetiva (gravada ou padrão). */
    @Transactional(readOnly = true)
    public Preference preference(UUID userId, String topic) {
        if (NotificationCatalog.TEST_TOPIC.equals(topic)) {
            return new Preference(topic, true, true, true, true, null, null, null);
        }
        Preference stored = stored(userId).get(topic);
        return stored != null ? stored : NotificationCatalog.defaults(topic);
    }

    @Transactional(readOnly = true)
    public Settings settings(UUID userId) {
        List<Settings> rows = jdbc.query("select quiet_enabled, quiet_start, quiet_end, quiet_allow_high, hide_details "
                + "from notification_settings where user_id = ?", (rs, i) -> new Settings(rs.getBoolean(1),
                        rs.getString(2), rs.getString(3), rs.getBoolean(4), rs.getBoolean(5)), userId);
        return rows.isEmpty() ? Settings.DEFAULT : rows.getFirst();
    }

    @Transactional(readOnly = true)
    public ZoneId zone(UUID userId) {
        try {
            return ZoneId.of(timeZone(userId));
        } catch (DateTimeException ex) {
            return ZoneId.of("UTC");
        }
    }

    /** Substitui as preferências enviadas (as outras continuam como estão) e, se vier, o horário de silêncio. */
    @Transactional
    public PreferencesView update(UUID userId, UpdateRequest request) {
        if (request == null) {
            throw DomainException.badRequest("invalid_preferences", "Envie as preferências");
        }
        List<Preference> preferences = request.preferences() == null ? List.of() : request.preferences();
        if (preferences.size() > MAX_PREFERENCES_PER_REQUEST) {
            throw DomainException.badRequest("too_many_preferences", "Preferências demais numa só requisição");
        }
        Set<UUID> owned = new HashSet<>();
        ownSources(userId).forEach(source -> owned.add(source.id()));
        Set<String> seen = new HashSet<>();
        List<Preference> valid = new ArrayList<>();
        for (Preference preference : preferences) {
            Preference checked = validate(preference, owned);
            if (!seen.add(checked.topic())) {
                throw DomainException.badRequest("duplicate_topic", "Tópico repetido: " + checked.topic());
            }
            valid.add(checked);
        }
        if (request.settings() != null) {
            Settings settings = validate(request.settings());
            jdbc.update("delete from notification_settings where user_id = ?", userId);
            jdbc.update("insert into notification_settings (user_id, quiet_enabled, quiet_start, quiet_end, quiet_allow_high, "
                    + "hide_details, updated_at) values (?, ?, ?, ?, ?, ?, ?)", userId, settings.quietEnabled(),
                    settings.quietStart(), settings.quietEnd(), settings.quietAllowHigh(), settings.hideDetails(),
                    Timestamp.from(clock.instant()));
        }
        for (Preference preference : valid) {
            jdbc.update("delete from notification_preference where user_id = ? and topic = ?", userId, preference.topic());
            jdbc.update("insert into notification_preference (user_id, topic, enabled, popup, system_alert, sound, "
                    + "lead_minutes, min_score, stale_days) values (?, ?, ?, ?, ?, ?, ?, ?, ?)", userId, preference.topic(),
                    preference.enabled(), preference.popup(), preference.system(), preference.sound(),
                    preference.leadMinutes() == null ? null : String.join(",",
                            preference.leadMinutes().stream().map(String::valueOf).toList()),
                    preference.minScore(), preference.staleDays());
        }
        return view(userId);
    }

    private Preference validate(Preference preference, Set<UUID> ownedSources) {
        if (preference == null || preference.topic() == null) {
            throw DomainException.badRequest("invalid_topic", "Tópico ausente");
        }
        String topic = preference.topic();
        boolean known = NotificationCatalog.isFixed(topic)
                || NotificationCatalog.sourceOf(topic).map(ownedSources::contains).orElse(false);
        if (!known) {
            // Fonte de outra pessoa e tópico inventado recebem a mesma resposta: não revela o que existe.
            throw DomainException.badRequest("invalid_topic", "Tópico desconhecido");
        }
        List<Integer> leads = null;
        if (NotificationCatalog.supportsLead(topic)) {
            leads = preference.leadMinutes() == null || preference.leadMinutes().isEmpty()
                    ? NotificationCatalog.defaults(topic).leadMinutes() : preference.leadMinutes();
            if (leads.size() > MAX_LEADS || leads.stream().anyMatch(lead -> lead == null || lead < MIN_LEAD_MINUTES
                    || lead > MAX_LEAD_MINUTES)) {
                throw DomainException.badRequest("invalid_lead", "Antecedência deve ter de 1 a " + MAX_LEADS
                        + " valores entre " + MIN_LEAD_MINUTES + " e " + MAX_LEAD_MINUTES + " minutos");
            }
            leads = leads.stream().distinct().sorted(Comparator.reverseOrder()).toList();
        } else if (preference.leadMinutes() != null && !preference.leadMinutes().isEmpty()) {
            throw DomainException.badRequest("invalid_lead", "Este tópico não aceita antecedência");
        }
        Integer minScore = null;
        if (preference.minScore() != null) {
            if (!NotificationCatalog.supportsMinScore(topic) || preference.minScore() < 0 || preference.minScore() > 100) {
                throw DomainException.badRequest("invalid_min_score", "Nota mínima deve ficar entre 0 e 100, só em fontes");
            }
            minScore = preference.minScore();
        }
        Integer staleDays = null;
        if (NotificationCatalog.supportsStaleDays(topic)) {
            staleDays = preference.staleDays() == null ? NotificationCatalog.DEFAULT_STALE_DAYS : preference.staleDays();
            if (staleDays < 1 || staleDays > MAX_STALE_DAYS) {
                throw DomainException.badRequest("invalid_stale_days", "Dias sem atualização devem ficar entre 1 e " + MAX_STALE_DAYS);
            }
        } else if (preference.staleDays() != null) {
            throw DomainException.badRequest("invalid_stale_days", "Este tópico não aceita dias sem atualização");
        }
        return new Preference(topic, preference.enabled(), preference.popup(), preference.system(), preference.sound(),
                leads, minScore, staleDays);
    }

    private static Settings validate(Settings settings) {
        if (settings.quietStart() == null || settings.quietEnd() == null || !TIME.matcher(settings.quietStart()).matches()
                || !TIME.matcher(settings.quietEnd()).matches()) {
            throw DomainException.badRequest("invalid_quiet_hours", "Use o formato HH:mm no horário de silêncio");
        }
        return settings;
    }

    /** Horário de silêncio agora, no fuso da conta. Início igual ao fim desliga o silêncio. */
    public boolean isQuiet(Settings settings, ZoneId zone) {
        if (!settings.quietEnabled()) {
            return false;
        }
        try {
            LocalTime start = LocalTime.parse(settings.quietStart());
            LocalTime end = LocalTime.parse(settings.quietEnd());
            LocalTime now = LocalTime.ofInstant(clock.instant(), zone);
            if (start.equals(end)) {
                return false;
            }
            return start.isBefore(end) ? !now.isBefore(start) && now.isBefore(end) : !now.isBefore(start) || now.isBefore(end);
        } catch (DateTimeParseException ex) {
            return false;
        }
    }

    private PreferenceView row(String topic, Group group, String key, String label, Map<String, Preference> stored) {
        Preference preference = stored.getOrDefault(topic, NotificationCatalog.defaults(topic));
        return new PreferenceView(topic, group, key, label, NotificationCatalog.priority(topic),
                NotificationCatalog.supportsLead(topic), NotificationCatalog.supportsStaleDays(topic),
                NotificationCatalog.supportsMinScore(topic), preference);
    }

    private Map<String, Preference> stored(UUID userId) {
        Map<String, Preference> map = new HashMap<>();
        jdbc.query("select topic, enabled, popup, system_alert, sound, lead_minutes, min_score, stale_days "
                + "from notification_preference where user_id = ?", rs -> {
                    String leads = rs.getString(6);
                    Integer minScore = (Integer) rs.getObject(7);
                    Integer staleDays = (Integer) rs.getObject(8);
                    map.put(rs.getString(1), new Preference(rs.getString(1), rs.getBoolean(2), rs.getBoolean(3),
                            rs.getBoolean(4), rs.getBoolean(5), leads == null || leads.isBlank() ? null
                                    : Arrays.stream(leads.split(",")).map(Integer::valueOf).toList(),
                            minScore, staleDays));
                }, userId);
        return map;
    }

    private List<SourceResponse> ownSources(UUID userId) {
        Map<UUID, SourceResponse> unique = new LinkedHashMap<>();
        sources.list(userId, null).forEach(source -> unique.putIfAbsent(source.id(), source));
        return List.copyOf(unique.values());
    }

    private String timeZone(UUID userId) {
        List<String> zones = jdbc.queryForList("select time_zone from app_user where id = ?", String.class, userId);
        return zones.isEmpty() || zones.getFirst() == null ? "UTC" : zones.getFirst();
    }
}
