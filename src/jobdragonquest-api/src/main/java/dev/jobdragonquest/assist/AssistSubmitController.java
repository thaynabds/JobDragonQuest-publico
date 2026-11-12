package dev.jobdragonquest.assist;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.jobdragonquest.common.security.CurrentUser;
import dev.jobdragonquest.common.text.UrlCanonicalizer;

/**
 * Registra, na candidatura da vaga salva, o envio que a pessoa confirmou na extensão. Chamado só depois do clique de
 * confirmação; a URL e a evidência vão no corpo, não na query.
 */
@RestController
@RequestMapping("/api/v1/assist")
class AssistSubmitController {

    private final ApplicationAssistant assistant;
    private final CurrentUser currentUser;

    AssistSubmitController(ApplicationAssistant assistant, CurrentUser currentUser) {
        this.assistant = assistant;
        this.currentUser = currentUser;
    }

    @PostMapping("/submitted")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void submitted(@Valid @RequestBody SubmittedRequest request) {
        assistant.registerSubmitted(currentUser.id(), request.url(), request.evidence(),
                Boolean.TRUE.equals(request.automatico()));
    }

    /** {@code automatico} nulo ou falso: envio com um clique confirmado pela pessoa. */
    record SubmittedRequest(@NotBlank @Size(max = UrlCanonicalizer.MAX_LENGTH) String url,
            @NotBlank @Size(max = 4000) String evidence, Boolean automatico) {
    }
}
