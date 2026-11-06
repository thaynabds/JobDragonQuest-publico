package dev.jobfly.sources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import dev.jobfly.applications.ApplicationService;
import dev.jobfly.common.domain.WorkMode;
import dev.jobfly.jobs.JobDtos.JobCreated;
import dev.jobfly.jobs.JobDtos.JobResponse;
import dev.jobfly.jobs.JobService;
import dev.jobfly.matching.MatchingEngine;
import dev.jobfly.profiles.ProfileService;
import dev.jobfly.sources.collect.AtsClient;
import dev.jobfly.sources.collect.CollectedJob;
import dev.jobfly.sources.collect.SkillCatalog;

/** Critério da issue #31: inserção concorrente da mesma vaga conta como duplicada e não derruba a coleta. */
class SourceCollectorRaceTest {

    @Test
    void concurrentInsertOfTheSameJobIsTreatedAsDuplicate() {
        Instant now = Instant.parse("2026-11-06T12:00:00Z");
        UUID user = UUID.randomUUID();
        WatchedSource source = new WatchedSource(user, SourceKind.GREENHOUSE, "chave", now);
        source.configure("Acme", "acme", null, 60, List.of(), now);

        WatchedSourceRepository sources = mock(WatchedSourceRepository.class);
        when(sources.findById(source.getId())).thenReturn(Optional.of(source));
        SourceRunRepository runs = mock(SourceRunRepository.class);
        when(runs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        AtsClient client = mock(AtsClient.class);
        when(client.kind()).thenReturn(SourceKind.GREENHOUSE);
        when(client.fetch("acme")).thenReturn(List.of(
                new CollectedJob("1", "Backend Engineer", "https://boards.greenhouse.io/acme/jobs/1", null, null, WorkMode.REMOTE, "Java"),
                new CollectedJob("2", "Data Engineer", "https://boards.greenhouse.io/acme/jobs/2", null, null, WorkMode.REMOTE, "Python")));
        JobService jobs = mock(JobService.class);
        JobResponse created = mock(JobResponse.class);
        when(created.id()).thenReturn(UUID.randomUUID());
        when(jobs.createFromSource(eq(user), eq(source.getId()), any()))
                .thenThrow(new DataIntegrityViolationException("uk_job_user_url"))
                .thenReturn(new JobCreated(created, false));
        ProfileService profiles = mock(ProfileService.class);
        when(profiles.snapshots(user)).thenReturn(List.of());
        PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
        when(tx.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        SourceCollector collector = new SourceCollector(sources, runs, List.of(client), new SkillCatalog(), jobs,
                profiles, new MatchingEngine(), mock(ApplicationService.class), Clock.fixed(now, ZoneOffset.UTC), tx);

        var run = collector.collect(source.getId(), SourceRun.Trigger.MANUAL);

        assertThat(run.status()).isEqualTo(WatchedSource.RunStatus.OK);
        assertThat(run.found()).isEqualTo(2);
        assertThat(run.created()).as("a vaga que perdeu a corrida não conta como nova").isEqualTo(1);
        assertThat(source.getLastStatus()).isEqualTo(WatchedSource.RunStatus.OK);
    }
}
