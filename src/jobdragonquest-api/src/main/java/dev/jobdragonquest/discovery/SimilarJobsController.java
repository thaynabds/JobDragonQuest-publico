package dev.jobdragonquest.discovery;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.jobdragonquest.applications.ApplicationService;
import dev.jobdragonquest.common.security.CurrentUser;
import dev.jobdragonquest.jobs.JobDtos.SimilarJob;
import dev.jobdragonquest.jobs.JobService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Vagas parecidas com as favoritas ou com as últimas candidaturas enviadas (issue #110). Só olha vagas da própria
 * conta; não busca nada fora do JobDragonQuest.
 */
@RestController
@Validated
@RequestMapping("/api/v1/jobs/similar")
class SimilarJobsController {

    /** De onde vêm as vagas de base. */
    enum Basis {
        FAVORITES,
        APPLIED
    }

    static final int RECENT_APPLIED = 10;

    private final JobService jobs;
    private final ApplicationService applications;
    private final CurrentUser currentUser;

    SimilarJobsController(JobService jobs, ApplicationService applications, CurrentUser currentUser) {
        this.jobs = jobs;
        this.applications = applications;
        this.currentUser = currentUser;
    }

    @GetMapping
    List<SimilarJob> similar(@RequestParam(defaultValue = "FAVORITES") Basis basis,
            @RequestParam(defaultValue = "30") @Min(0) @Max(100) int minScore,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        UUID userId = currentUser.id();
        Set<UUID> favorites = jobs.favoriteIds(userId);
        Set<UUID> applied = applications.jobIdsWithApplication(userId);
        Set<UUID> bases = basis == Basis.FAVORITES ? favorites : applications.recentSubmittedJobIds(userId, RECENT_APPLIED);
        // Não sugere o que a pessoa já marcou ou já se candidatou.
        Set<UUID> exclude = new HashSet<>(favorites);
        exclude.addAll(applied);
        return jobs.similar(userId, bases, exclude, minScore, limit);
    }
}
