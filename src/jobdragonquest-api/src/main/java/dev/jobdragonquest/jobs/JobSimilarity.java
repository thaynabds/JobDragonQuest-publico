package dev.jobdragonquest.jobs;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import dev.jobdragonquest.common.text.SkillNormalizer;
import dev.jobdragonquest.common.text.TitleMatcher;

/**
 * Semelhança entre vagas, determinística e explicável: índice de Jaccard das palavras do cargo (pelo radical) e das
 * competências pedidas. Com competências dos dois lados, cada parte pesa metade; sem elas, vale só o cargo.
 */
final class JobSimilarity {

    /** Palavras que aparecem em quase todo cargo e não dizem nada sobre a vaga. */
    private static final Set<String> GENERIC = Set.of("vaga", "job", "remot", "remote", "hibrid", "hybrid", "presencial",
            "onsite", "pcd", "pleno", "plen", "junior", "senior", "jr", "sr", "pl", "ii", "iii", "i", "br", "brasil", "brazil");

    record Profile(UUID id, String title, Set<String> words, Set<String> skills) {

        static Profile of(JobPosting job) {
            Set<String> words = new LinkedHashSet<>(TitleMatcher.keyWords(job.getTitle()));
            words.removeAll(GENERIC);
            Set<String> skills = new LinkedHashSet<>();
            for (JobRequirement requirement : job.getRequirements()) {
                if (requirement.getSkill() != null) {
                    skills.add(SkillNormalizer.normalize(requirement.getSkill()));
                }
            }
            return new Profile(job.getId(), job.getTitle(), Set.copyOf(words), Set.copyOf(skills));
        }
    }

    record Result(int score, List<String> sharedWords, List<String> sharedSkills) {
    }

    private JobSimilarity() {
    }

    static Result compare(Profile candidate, Profile basis) {
        List<String> words = intersection(candidate.words(), basis.words());
        List<String> skills = intersection(candidate.skills(), basis.skills());
        double titlePart = jaccard(words.size(), candidate.words(), basis.words());
        double value;
        if (!candidate.skills().isEmpty() && !basis.skills().isEmpty()) {
            value = 0.5 * titlePart + 0.5 * jaccard(skills.size(), candidate.skills(), basis.skills());
        } else {
            value = titlePart;
        }
        return new Result((int) Math.round(value * 100), words, skills);
    }

    private static double jaccard(int shared, Set<String> a, Set<String> b) {
        int union = a.size() + b.size() - shared;
        return union == 0 ? 0 : (double) shared / union;
    }

    private static List<String> intersection(Set<String> a, Set<String> b) {
        List<String> shared = new ArrayList<>();
        for (String value : a) {
            if (b.contains(value)) {
                shared.add(value);
            }
        }
        shared.sort(null);
        return List.copyOf(shared);
    }
}
