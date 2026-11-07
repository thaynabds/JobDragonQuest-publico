package dev.jobdragonquest.settings;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.jobdragonquest.common.security.CurrentUser;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1/me/accessibility")
class AccessibilityController {

    private final AccessibilitySettings settings;
    private final CurrentUser currentUser;

    AccessibilityController(AccessibilitySettings settings, CurrentUser currentUser) {
        this.settings = settings;
        this.currentUser = currentUser;
    }

    /** 204 quando a conta ainda não salvou preferências (o painel fica com as do navegador). */
    @GetMapping
    ResponseEntity<JsonNode> get() {
        JsonNode value = settings.get(currentUser.id());
        return value == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(value);
    }

    @PutMapping
    JsonNode save(@RequestBody JsonNode body) {
        return settings.save(currentUser.id(), body);
    }
}
