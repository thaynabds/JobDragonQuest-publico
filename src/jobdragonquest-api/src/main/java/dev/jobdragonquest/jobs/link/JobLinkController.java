package dev.jobdragonquest.jobs.link;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Set;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.jobdragonquest.common.error.DomainException;
import dev.jobdragonquest.common.security.CurrentUser;
import dev.jobdragonquest.jobs.JobDtos.JobRequest;
import dev.jobdragonquest.jobs.JobDtos.RequirementDto;
import dev.jobdragonquest.jobs.JobRequirement;
import dev.jobdragonquest.sources.collect.SkillCatalog;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Lê a página de uma vaga a partir do link e devolve um rascunho para a pessoa conferir e salvar (issue #114). Não
 * grava nada: o cadastro continua pelo POST /jobs, com a deduplicação de sempre.
 */
@RestController
@RequestMapping("/api/v1/jobs/from-link")
class JobLinkController {

    static final int MAX_REQUIREMENTS = 40;

    /** Link colado pela pessoa. */
    record LinkRequest(@NotBlank @Size(max = 2048) String url) {
    }

    /** Rascunho no formato do cadastro de vaga. {@code extractedFrom}: JSON_LD (dados estruturados) ou META. */
    record LinkDraft(JobRequest job, String extractedFrom) {
    }

    private final SafeWebFetcher fetcher;
    private final SkillCatalog skills;
    private final CurrentUser currentUser;

    JobLinkController(SafeWebFetcher fetcher, SkillCatalog skills, CurrentUser currentUser) {
        this.fetcher = fetcher;
        this.skills = skills;
        this.currentUser = currentUser;
    }

    @PostMapping
    LinkDraft read(@Valid @RequestBody LinkRequest request) {
        currentUser.id();
        URI uri;
        try {
            uri = new URI(request.url().strip());
        } catch (URISyntaxException ex) {
            throw DomainException.badRequest("invalid_url", "Link inválido.");
        }
        String html = fetcher.fetchHtml(uri);
        JobPageExtractor.Extracted data = JobPageExtractor.extract(html, uri);
        if (data.title() == null) {
            throw DomainException.badRequest("link_no_job", "Não encontrei os dados de uma vaga nessa página. Cadastre à mão.");
        }
        Set<String> mentioned = skills.detect(data.title() + "\n" + (data.description() == null ? "" : data.description()));
        List<RequirementDto> requirements = mentioned.stream()
                .limit(MAX_REQUIREMENTS)
                .map(skill -> new RequirementDto(JobRequirement.Kind.DESIRED, "Mencionado na descrição: " + skill, skill, null))
                .toList();
        JobRequest job = new JobRequest("LINK", uri.toString(), data.company() == null ? "" : data.company(), data.title(),
                data.location(), data.country(), data.workMode(), null, data.description(), data.salaryMin(), data.salaryMax(),
                data.salaryCurrency(), data.salaryPeriod(), requirements, List.of());
        return new LinkDraft(job, data.from());
    }
}
