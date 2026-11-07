package dev.jobdragonquest.settings;

import java.time.Clock;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.jobdragonquest.common.error.DomainException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Preferências de acessibilidade da conta (issue #126). Aceita só as chaves conhecidas da central de acessibilidade,
 * com valores booleanos, os temas e filtros da lista e os tamanhos de fonte permitidos. Qualquer outra coisa é
 * recusada: nada estranho vira classe ou estilo na página de quem abrir a conta.
 */
@Service
public class AccessibilitySettings {

    static final String KEY = "accessibility";
    static final Set<String> THEMES = Set.of("system", "light", "dark", "high_contrast", "kuro", "pink_void", "red_void", "yellow_void");
    static final Set<String> FILTERS = Set.of("none", "invert", "smart_invert", "protanopia", "deuteranopia", "tritanopia", "grayscale");
    static final Set<Integer> FONT_SCALES = Set.of(80, 90, 100, 110, 120, 130, 140);
    /** Os mesmos recursos de liga e desliga do painel (src/jobdragonquest-web/src/a11y/prefs.ts). */
    static final Set<String> TOGGLES = Set.of("contrast", "saturate", "hideImages", "dyslexiaFont", "lineHeight", "spacing",
            "highlightLinks", "highlightHeadings", "pauseAnimations", "pauseVideos", "bigCursor", "focusRing", "readingGuide");
    private static final int MAX_KEYS = 40;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final AccountSettingRepository settings;
    private final Clock clock;

    AccessibilitySettings(AccountSettingRepository settings, Clock clock) {
        this.settings = settings;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public JsonNode get(UUID userId) {
        return settings.findById(new AccountSetting.Key(userId, KEY)).map(s -> JSON.readTree(s.getValue())).orElse(null);
    }

    @Transactional
    public JsonNode save(UUID userId, JsonNode body) {
        ObjectNode clean = validate(body);
        String value = JSON.writeValueAsString(clean);
        AccountSetting.Key key = new AccountSetting.Key(userId, KEY);
        settings.findById(key).ifPresentOrElse(s -> s.change(value, clock.instant()),
                () -> settings.save(new AccountSetting(key, value, clock.instant())));
        return clean;
    }

    static ObjectNode validate(JsonNode body) {
        if (body == null || !body.isObject() || body.size() > MAX_KEYS) {
            throw invalid();
        }
        ObjectNode clean = JSON.createObjectNode();
        Iterator<Map.Entry<String, JsonNode>> fields = body.properties().iterator();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String name = field.getKey();
            JsonNode value = field.getValue();
            switch (name) {
                case "theme" -> {
                    if (!value.isString() || !THEMES.contains(value.asString())) throw invalid();
                    clean.put(name, value.asString());
                }
                case "colorFilter" -> {
                    if (!value.isString() || !FILTERS.contains(value.asString())) throw invalid();
                    clean.put(name, value.asString());
                }
                case "fontScale" -> {
                    if (!value.isInt() || !FONT_SCALES.contains(value.asInt())) throw invalid();
                    clean.put(name, value.asInt());
                }
                default -> {
                    if (!TOGGLES.contains(name) || !value.isBoolean()) throw invalid();
                    clean.put(name, value.asBoolean());
                }
            }
        }
        return clean;
    }

    private static DomainException invalid() {
        return DomainException.badRequest("invalid_accessibility", "Preferências de acessibilidade inválidas.");
    }
}
