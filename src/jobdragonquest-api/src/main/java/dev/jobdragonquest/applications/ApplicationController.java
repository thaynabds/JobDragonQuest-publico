package dev.jobdragonquest.applications;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
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

import dev.jobdragonquest.applications.ApplicationDtos.ApplicationDetail;
import dev.jobdragonquest.applications.ApplicationDtos.BatchFailure;
import dev.jobdragonquest.applications.ApplicationDtos.BatchTransitionRequest;
import dev.jobdragonquest.applications.ApplicationDtos.BatchTransitionResult;
import dev.jobdragonquest.applications.ApplicationEnums.EventSource;
import dev.jobdragonquest.common.error.DomainException;
import dev.jobdragonquest.common.error.NotFoundException;
import dev.jobdragonquest.applications.ApplicationDtos.ApplicationResponse;
import dev.jobdragonquest.applications.ApplicationDtos.BoardColumn;
import dev.jobdragonquest.applications.ApplicationDtos.CreateApplicationRequest;
import dev.jobdragonquest.applications.ApplicationDtos.CreateResult;
import dev.jobdragonquest.applications.ApplicationDtos.EventResponse;
import dev.jobdragonquest.applications.ApplicationDtos.NoteRequest;
import dev.jobdragonquest.applications.ApplicationDtos.TransitionOutcome;
import dev.jobdragonquest.applications.ApplicationDtos.TransitionRequest;
import dev.jobdragonquest.applications.ApplicationDtos.TransitionResult;
import dev.jobdragonquest.applications.ApplicationDtos.UpdateApplicationRequest;
import dev.jobdragonquest.common.security.CurrentUser;
import dev.jobdragonquest.common.web.PageResponse;
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

    /**
     * Muda a etapa de várias candidaturas, cada uma na sua transação: uma recusa (etapa inválida, candidatura que não é
     * da conta) não desfaz as outras. Candidatura de outra conta volta como not_found, igual a uma que não existe.
     */
    @PostMapping("/batch/transitions")
    BatchTransitionResult batchTransition(@Valid @RequestBody BatchTransitionRequest request) {
        UUID userId = currentUser.id();
        int updated = 0;
        List<BatchFailure> failed = new ArrayList<>();
        for (UUID applicationId : new LinkedHashSet<>(request.ids())) {
            try {
                TransitionResult result = applications.transition(userId, applicationId, new TransitionRequest(request.toState(),
                        EventSource.MANUAL, request.evidence(), null, null, null, null));
                if (result.outcome() == TransitionOutcome.APPLIED) {
                    updated++;
                }
            } catch (NotFoundException ex) {
                failed.add(new BatchFailure(applicationId, "not_found"));
            } catch (DomainException ex) {
                failed.add(new BatchFailure(applicationId, ex.code()));
            }
        }
        return new BatchTransitionResult(updated, List.copyOf(failed));
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
