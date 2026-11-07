package dev.jobdragonquest.assist;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.jobdragonquest.assist.ApplicationAssistant.Review;
import dev.jobdragonquest.assist.ApplicationAssistant.SuggestedAnswer;
import dev.jobdragonquest.common.security.CurrentUser;

/** Perguntas e respostas sugeridas e revisão antes de enviar, para uma vaga e um perfil da própria conta. */
@RestController
@RequestMapping("/api/v1/jobs/{jobId}/assist")
class AssistController {

    private final ApplicationAssistant assistant;
    private final CurrentUser currentUser;

    AssistController(ApplicationAssistant assistant, CurrentUser currentUser) {
        this.assistant = assistant;
        this.currentUser = currentUser;
    }

    @GetMapping("/answers")
    List<SuggestedAnswer> answers(@PathVariable UUID jobId, @RequestParam UUID profileId) {
        return assistant.answers(currentUser.id(), jobId, profileId);
    }

    @GetMapping("/review")
    Review review(@PathVariable UUID jobId, @RequestParam UUID profileId) {
        return assistant.review(currentUser.id(), jobId, profileId);
    }
}
