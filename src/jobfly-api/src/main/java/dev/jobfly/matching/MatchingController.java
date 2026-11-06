package dev.jobfly.matching;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.jobfly.common.security.CurrentUser;
import dev.jobfly.jobs.JobService;
import dev.jobfly.jobs.JobSnapshot;
import dev.jobfly.profiles.ProfileService;

@RestController
@RequestMapping("/api/v1/jobs/{jobId}/matches")
class MatchingController {

    private final MatchingEngine engine;
    private final JobService jobs;
    private final ProfileService profiles;
    private final CurrentUser currentUser;

    MatchingController(MatchingEngine engine, JobService jobs, ProfileService profiles, CurrentUser currentUser) {
        this.engine = engine;
        this.jobs = jobs;
        this.profiles = profiles;
        this.currentUser = currentUser;
    }

    /** Todos os perfis do usuário, do mais para o menos adequado. O primeiro é o recomendado. */
    @GetMapping
    List<MatchResult> rank(@PathVariable UUID jobId) {
        UUID userId = currentUser.id();
        JobSnapshot job = jobs.snapshot(userId, jobId);
        return engine.rank(profiles.snapshots(userId), job);
    }

    @GetMapping("/{profileId}")
    MatchResult one(@PathVariable UUID jobId, @PathVariable UUID profileId) {
        UUID userId = currentUser.id();
        return engine.evaluate(profiles.snapshot(userId, profileId), jobs.snapshot(userId, jobId));
    }
}
