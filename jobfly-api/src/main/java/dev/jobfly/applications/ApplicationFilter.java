package dev.jobfly.applications;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import dev.jobfly.common.text.TextNormalizer;
import jakarta.persistence.criteria.Predicate;

/** Filtros do painel (RF-07.5). Todos são opcionais e se combinam com E. O usuário é sempre filtrado. */
public record ApplicationFilter(
        ApplicationState state,
        UUID profileId,
        String platform,
        String company,
        String country,
        Instant appliedFrom,
        Instant appliedTo,
        boolean staleOnly) {

    public static ApplicationFilter none() {
        return new ApplicationFilter(null, null, null, null, null, null, null, false);
    }

    Specification<JobApplication> toSpecification(UUID userId, Instant staleThreshold) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("userId"), userId));
            if (state != null) {
                predicates.add(cb.equal(root.get("state"), state));
            }
            if (profileId != null) {
                predicates.add(cb.equal(root.get("profileId"), profileId));
            }
            if (platform != null && !platform.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("platform")), platform.strip().toLowerCase(Locale.ROOT)));
            }
            if (company != null && !company.isBlank()) {
                // Parâmetro ligado pelo JPA: sem concatenação de SQL. Curingas do LIKE são escapados.
                String term = escapeLike(TextNormalizer.normalizeCompany(company));
                predicates.add(cb.like(root.get("companyNorm"), "%" + term + "%", '\\'));
            }
            if (country != null && !country.isBlank()) {
                predicates.add(cb.equal(root.get("country"), country.strip().toUpperCase(Locale.ROOT)));
            }
            if (appliedFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("appliedAt"), appliedFrom));
            }
            if (appliedTo != null) {
                predicates.add(cb.lessThan(root.get("appliedAt"), appliedTo));
            }
            if (staleOnly) {
                List<ApplicationState> active = Arrays.stream(ApplicationState.values())
                        .filter(ApplicationState::isActive).toList();
                predicates.add(root.get("state").in(active));
                predicates.add(cb.lessThan(root.get("lastEvidenceAt"), staleThreshold));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
