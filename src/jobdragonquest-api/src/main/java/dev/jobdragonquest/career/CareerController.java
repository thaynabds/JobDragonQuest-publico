package dev.jobdragonquest.career;

import java.util.List;
import java.util.UUID;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.jobdragonquest.career.CareerService.CoverLetter;
import dev.jobdragonquest.career.CareerService.InterviewQuestion;
import dev.jobdragonquest.career.CareerService.Resume;
import dev.jobdragonquest.career.CareerService.SalaryStats;
import dev.jobdragonquest.career.CareerService.SkillDemand;
import dev.jobdragonquest.common.security.CurrentUser;
import jakarta.validation.constraints.Size;

/** Currículo, carta, entrevista, salário e competências: só dados da própria conta (issue #118). */
@RestController
@Validated
class CareerController {

    private final CareerService career;
    private final CurrentUser currentUser;

    CareerController(CareerService career, CurrentUser currentUser) {
        this.career = career;
        this.currentUser = currentUser;
    }

    @GetMapping("/api/v1/profiles/{profileId}/resume")
    Resume resume(@PathVariable UUID profileId, @RequestParam(required = false) UUID jobId) {
        return career.resume(currentUser.id(), profileId, jobId);
    }

    @GetMapping("/api/v1/profiles/{profileId}/cover-letter")
    CoverLetter coverLetter(@PathVariable UUID profileId, @RequestParam(required = false) UUID jobId) {
        return career.coverLetter(currentUser.id(), profileId, jobId);
    }

    @GetMapping("/api/v1/jobs/{jobId}/interview")
    List<InterviewQuestion> interview(@PathVariable UUID jobId, @RequestParam UUID profileId) {
        return career.interview(currentUser.id(), jobId, profileId);
    }

    @GetMapping("/api/v1/career/salary")
    List<SalaryStats> salary(@RequestParam(required = false) @Size(max = 160) String title) {
        return career.salary(currentUser.id(), title);
    }

    @GetMapping("/api/v1/career/skills")
    List<SkillDemand> skills(@RequestParam(required = false) UUID profileId, @RequestParam(required = false) @Size(max = 160) String title) {
        return career.skills(currentUser.id(), profileId, title);
    }
}
