package dev.jobdragonquest.assist;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.jobdragonquest.assist.ApplicationAssistant.Prefill;
import dev.jobdragonquest.common.security.CurrentUser;
import dev.jobdragonquest.common.text.UrlCanonicalizer;

/**
 * Dados para preencher a candidatura pela extensão, a partir da vaga salva com a URL da página. Só leitura: a pessoa
 * revisa e envia pelo próprio site. A URL vai no corpo, não na query, para não parar em log de acesso.
 */
@RestController
@RequestMapping("/api/v1/assist")
class AssistPrefillController {

    private final ApplicationAssistant assistant;
    private final CurrentUser currentUser;

    AssistPrefillController(ApplicationAssistant assistant, CurrentUser currentUser) {
        this.assistant = assistant;
        this.currentUser = currentUser;
    }

    @PostMapping("/prefill")
    Prefill prefill(@Valid @RequestBody PrefillRequest request) {
        return assistant.prefill(currentUser.id(), request.url());
    }

    record PrefillRequest(@NotBlank @Size(max = UrlCanonicalizer.MAX_LENGTH) String url) {
    }
}
