package dev.jobdragonquest.applications;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.jobdragonquest.applications.ApplicationDtos.ContactRequest;
import dev.jobdragonquest.applications.ApplicationDtos.ContactResponse;
import dev.jobdragonquest.applications.ApplicationDtos.TransitionRequest;
import dev.jobdragonquest.applications.ApplicationEnums.EventSource;
import dev.jobdragonquest.audit.AuditService;
import dev.jobdragonquest.common.error.DomainException;
import dev.jobdragonquest.common.error.NotFoundException;
import dev.jobdragonquest.common.text.UrlCanonicalizer;

/**
 * Contatos de uma candidatura. Só a conta dona da candidatura lê ou grava; a de outra conta responde 404, igual a uma
 * inexistente. A auditoria registra a ação e o id, nunca nome, e-mail ou link da pessoa.
 */
@Service
public class ApplicationContactService {

    static final int MAX_CONTACTS = 20;

    private final ApplicationRepository applications;
    private final ApplicationContactRepository contacts;
    private final ApplicationService applicationService;
    private final AuditService audit;
    private final Clock clock;

    ApplicationContactService(ApplicationRepository applications, ApplicationContactRepository contacts,
            ApplicationService applicationService, AuditService audit, Clock clock) {
        this.applications = applications;
        this.contacts = contacts;
        this.applicationService = applicationService;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ContactResponse> list(UUID userId, UUID applicationId) {
        owned(userId, applicationId);
        return contacts.findByApplicationIdOrderByCreatedAtAsc(applicationId).stream().map(ContactResponse::of).toList();
    }

    /**
     * Grava o contato. Com {@code contacted = true} numa candidatura recém-enviada (enviada ou confirmada), leva a
     * candidatura para "contato com recrutador", como mudança manual com o contato como evidência. Uma candidatura já
     * mais adiante no processo não volta de etapa.
     */
    @Transactional
    public ContactResponse add(UUID userId, UUID applicationId, ContactRequest request) {
        JobApplication application = owned(userId, applicationId);
        if (contacts.countByApplicationId(applicationId) >= MAX_CONTACTS) {
            throw DomainException.conflict("contact_limit", "Cada candidatura guarda até " + MAX_CONTACTS + " contatos.");
        }
        Instant now = clock.instant();
        boolean contacted = Boolean.TRUE.equals(request.contacted());
        ApplicationContact contact = contacts.save(new ApplicationContact(applicationId, request.role(), request.name().strip(),
                blankToNull(request.position()), email(request.email()), UrlCanonicalizer.optional(blankToNull(request.linkedinUrl())),
                blankToNull(request.notes()), contacted ? now : null, now));
        audit.record(userId, "application.contact_added", "application", applicationId, contact.getId().toString());
        if (contacted && application.getState().phase() == ApplicationState.Phase.INTAKE) {
            applicationService.transition(userId, applicationId, new TransitionRequest(ApplicationState.RECRUITER_CONTACT,
                    EventSource.MANUAL, "Contato registrado: " + contact.getName() + " (" + contact.getRole() + ")",
                    null, null, null, null));
        }
        return ContactResponse.of(contact);
    }

    @Transactional
    public void delete(UUID userId, UUID applicationId, UUID contactId) {
        owned(userId, applicationId);
        ApplicationContact contact = contacts.findByIdAndApplicationId(contactId, applicationId)
                .orElseThrow(() -> new NotFoundException("Contato"));
        contacts.delete(contact);
        audit.record(userId, "application.contact_deleted", "application", applicationId, contactId.toString());
    }

    private JobApplication owned(UUID userId, UUID applicationId) {
        return applications.findByIdAndUserId(applicationId, userId).orElseThrow(() -> new NotFoundException("Candidatura"));
    }

    private static String email(String value) {
        String email = blankToNull(value);
        if (email == null) {
            return null;
        }
        // Validação simples e sem regex ambígua: um @, partes não vazias, domínio com ponto e sem espaços.
        int at = email.indexOf('@');
        boolean valid = at > 0 && at == email.lastIndexOf('@') && at < email.length() - 1
                && email.substring(at + 1).contains(".") && email.chars().noneMatch(Character::isWhitespace);
        if (!valid) {
            throw DomainException.badRequest("invalid_email", "E-mail do contato inválido.");
        }
        return email;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
