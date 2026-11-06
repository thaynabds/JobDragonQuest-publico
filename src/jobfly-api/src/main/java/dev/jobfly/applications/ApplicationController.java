package dev.jobfly.applications;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.jobfly.applications.ApplicationDtos.ApplicationDetail;
import dev.jobfly.applications.ApplicationDtos.ApplicationResponse;
import dev.jobfly.applications.ApplicationDtos.BoardColumn;
import dev.jobfly.applications.ApplicationDtos.CreateApplicationRequest;
import dev.jobfly.applications.ApplicationDtos.CreateResult;
import dev.jobfly.applications.ApplicationDtos.EventResponse;
import dev.jobfly.applications.ApplicationDtos.NoteRequest;
import dev.jobfly.applications.ApplicationDtos.TransitionRequest;
import dev.jobfly.applications.ApplicationDtos.TransitionResult;
import dev.jobfly.applications.ApplicationDtos.UpdateApplicationRequest;
import dev.jobfly.common.security.CurrentUser;
import dev.jobfly.common.web.PageResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/applications")
class ApplicationController {

    private final ApplicationService applications;
    private final CurrentUser currentUser;

    ApplicationController(ApplicationService applications, CurrentUser currentUser) {
        this.applications = applications;
        this.currentUser = currentUser;
    }

    @GetMapping
    PageResponse<ApplicationResponse> list(
            @RequestParam(required = false) ApplicationState state,
            @RequestParam(required = false) UUID profileId,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String company,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant appliedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant appliedTo,
            @RequestParam(defaultValue = "false") boolean staleOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        ApplicationFilter filter = new ApplicationFilter(state, profileId, platform, company, country, appliedFrom, appliedTo, staleOnly);
        return PageResponse.of(applications.list(currentUser.id(), filter, page, size));
    }

    @GetMapping("/board")
    List<BoardColumn> board(
            @RequestParam(required = false) UUID profileId,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String company,
            @RequestParam(required = false) String country) {
        return applications.board(currentUser.id(),
                new ApplicationFilter(null, profileId, platform, company, country, null, null, false));
    }

    /**
     * 201 para candidatura nova. 200 com X-Duplicate-Of quando ela já existia. 200 com Idempotent-Replayed quando a
     * mesma Idempotency-Key foi reenviada.
     */
    @PostMapping
    ResponseEntity<ApplicationResponse> create(@Valid @RequestBody CreateApplicationRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        CreateResult result = applications.create(currentUser.id(), request, idempotencyKey);
        URI location = URI.create("/api/v1/applications/" + result.application().id());
        if (result.replayed()) {
            return ResponseEntity.ok().location(location).header("Idempotent-Replayed", "true").body(result.application());
        }
        if (result.duplicate()) {
            return ResponseEntity.ok().location(location)
                    .header("X-Duplicate-Of", result.application().id().toString()).body(result.application());
        }
        return ResponseEntity.created(location).body(result.application());
    }

    @GetMapping("/{id}")
    ApplicationDetail get(@PathVariable UUID id) {
        return applications.detail(currentUser.id(), id);
    }

    @PutMapping("/{id}")
    ApplicationResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateApplicationRequest request) {
        return applications.update(currentUser.id(), id, request);
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable UUID id) {
        applications.delete(currentUser.id(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/transitions")
    ResponseEntity<TransitionResult> transition(@PathVariable UUID id, @Valid @RequestBody TransitionRequest request) {
        TransitionResult result = applications.transition(currentUser.id(), id, request);
        HttpStatus status = switch (result.outcome()) {
            case APPLIED -> HttpStatus.OK;
            case SUGGESTED -> HttpStatus.ACCEPTED;
            case NOTED -> HttpStatus.OK;
        };
        return ResponseEntity.status(status).body(result);
    }

    @PostMapping("/{id}/notes")
    ResponseEntity<EventResponse> note(@PathVariable UUID id, @Valid @RequestBody NoteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(applications.addNote(currentUser.id(), id, request.text()));
    }

    @PostMapping("/{id}/suggestions/{eventId}/accept")
    TransitionResult accept(@PathVariable UUID id, @PathVariable UUID eventId) {
        return applications.acceptSuggestion(currentUser.id(), id, eventId);
    }

    @PostMapping("/{id}/suggestions/{eventId}/dismiss")
    ResponseEntity<Void> dismiss(@PathVariable UUID id, @PathVariable UUID eventId) {
        applications.dismissSuggestion(currentUser.id(), id, eventId);
        return ResponseEntity.noContent().build();
    }
}
