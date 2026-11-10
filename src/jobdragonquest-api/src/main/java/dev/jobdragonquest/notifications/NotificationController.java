package dev.jobdragonquest.notifications;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.jobdragonquest.common.security.CurrentUser;
import dev.jobdragonquest.notifications.NotificationDtos.CountResponse;
import dev.jobdragonquest.notifications.NotificationDtos.IdsRequest;
import dev.jobdragonquest.notifications.NotificationDtos.NotificationList;
import dev.jobdragonquest.notifications.NotificationDtos.NotificationResponse;
import dev.jobdragonquest.notifications.NotificationDtos.SnoozeRequest;
import dev.jobdragonquest.notifications.NotificationPreferences.PreferencesView;
import dev.jobdragonquest.notifications.NotificationPreferences.UpdateRequest;

@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController {

    private final NotificationService notifications;
    private final NotificationGenerator generator;
    private final NotificationPreferences preferences;
    private final CurrentUser currentUser;

    NotificationController(NotificationService notifications, NotificationGenerator generator,
            NotificationPreferences preferences, CurrentUser currentUser) {
        this.notifications = notifications;
        this.generator = generator;
        this.preferences = preferences;
        this.currentUser = currentUser;
    }

    @GetMapping
    NotificationList list(@RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        UUID userId = currentUser.id();
        generator.refresh(userId);
        return notifications.list(userId, unreadOnly, page, size);
    }

    /** O painel consulta aqui a cada poucos segundos para mostrar pop-ups e notificações do sistema. */
    @GetMapping("/due")
    List<NotificationResponse> due() {
        UUID userId = currentUser.id();
        generator.refresh(userId);
        return notifications.due(userId);
    }

    @PostMapping("/delivered")
    CountResponse delivered(@RequestBody IdsRequest request) {
        return new CountResponse(notifications.markDelivered(currentUser.id(), request == null ? null : request.ids()));
    }

    @PostMapping("/{id}/read")
    NotificationResponse read(@PathVariable UUID id) {
        return notifications.markRead(currentUser.id(), id);
    }

    @PostMapping("/read-all")
    CountResponse readAll() {
        return new CountResponse(notifications.markAllRead(currentUser.id()));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> dismiss(@PathVariable UUID id) {
        notifications.dismiss(currentUser.id(), id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    CountResponse dismissAll() {
        return new CountResponse(notifications.dismissAll(currentUser.id()));
    }

    @PostMapping("/{id}/snooze")
    NotificationResponse snooze(@PathVariable UUID id, @RequestBody SnoozeRequest request) {
        return notifications.snooze(currentUser.id(), id, request == null ? 0 : request.minutes());
    }

    @PostMapping("/manual")
    @ResponseStatus(HttpStatus.CREATED)
    NotificationResponse createManual(@Valid @RequestBody NotificationDtos.ManualRequest request) {
        return notifications.createManual(currentUser.id(), request);
    }

    @PostMapping("/test")
    NotificationResponse test() {
        return notifications.test(currentUser.id());
    }

    @GetMapping("/preferences")
    PreferencesView preferences() {
        return preferences.view(currentUser.id());
    }

    @PutMapping("/preferences")
    PreferencesView updatePreferences(@RequestBody UpdateRequest request) {
        return preferences.update(currentUser.id(), request);
    }
}
