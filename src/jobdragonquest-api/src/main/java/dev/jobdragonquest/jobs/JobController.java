package dev.jobdragonquest.jobs;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.jobdragonquest.common.security.CurrentUser;
import dev.jobdragonquest.common.web.PageResponse;
import dev.jobdragonquest.jobs.JobDtos.JobCreated;
import dev.jobdragonquest.jobs.JobDtos.JobRequest;
import dev.jobdragonquest.jobs.JobDtos.JobResponse;
import dev.jobdragonquest.jobs.JobDtos.JobSummary;
import dev.jobdragonquest.jobs.JobDtos.StatusRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/jobs")
class JobController {

    private final JobService jobs;
    private final CurrentUser currentUser;

    JobController(JobService jobs, CurrentUser currentUser) {
        this.jobs = jobs;
        this.currentUser = currentUser;
    }

    @GetMapping
    PageResponse<JobSummary> list(@RequestParam(required = false) UUID sourceId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(jobs.list(currentUser.id(), sourceId, page, size));
    }

    @GetMapping("/{id}")
    JobResponse get(@PathVariable UUID id) {
        return jobs.get(currentUser.id(), id);
    }

    /** 201 para vaga nova. 200 com X-Duplicate-Of quando ela já existia (RN-06). */
    @PostMapping
    ResponseEntity<JobResponse> create(@Valid @RequestBody JobRequest request) {
        JobCreated created = jobs.create(currentUser.id(), request);
        URI location = URI.create("/api/v1/jobs/" + created.job().id());
        if (created.duplicate()) {
            return ResponseEntity.ok().location(location).header("X-Duplicate-Of", created.job().id().toString()).body(created.job());
        }
        return ResponseEntity.created(location).body(created.job());
    }

    @PutMapping("/{id}")
    JobResponse update(@PathVariable UUID id, @Valid @RequestBody JobRequest request) {
        return jobs.update(currentUser.id(), id, request);
    }

    @PatchMapping("/{id}/status")
    JobResponse status(@PathVariable UUID id, @Valid @RequestBody StatusRequest request) {
        return jobs.changeStatus(currentUser.id(), id, request.status());
    }
}
