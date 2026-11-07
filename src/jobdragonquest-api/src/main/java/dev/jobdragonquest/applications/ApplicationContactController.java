package dev.jobdragonquest.applications;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.jobdragonquest.applications.ApplicationDtos.ContactRequest;
import dev.jobdragonquest.applications.ApplicationDtos.ContactResponse;
import dev.jobdragonquest.common.security.CurrentUser;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/applications/{applicationId}/contacts")
class ApplicationContactController {

    private final ApplicationContactService contacts;
    private final CurrentUser currentUser;

    ApplicationContactController(ApplicationContactService contacts, CurrentUser currentUser) {
        this.contacts = contacts;
        this.currentUser = currentUser;
    }

    @GetMapping
    List<ContactResponse> list(@PathVariable UUID applicationId) {
        return contacts.list(currentUser.id(), applicationId);
    }

    @PostMapping
    ResponseEntity<ContactResponse> add(@PathVariable UUID applicationId, @Valid @RequestBody ContactRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(contacts.add(currentUser.id(), applicationId, request));
    }

    @DeleteMapping("/{contactId}")
    ResponseEntity<Void> delete(@PathVariable UUID applicationId, @PathVariable UUID contactId) {
        contacts.delete(currentUser.id(), applicationId, contactId);
        return ResponseEntity.noContent().build();
    }
}
