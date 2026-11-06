package dev.jobfly.sources.collect;

import dev.jobfly.common.domain.WorkMode;

/** Vaga como veio do ATS, antes de virar uma vaga do JobFly. Campos opcionais podem ser null. */
public record CollectedJob(String externalId, String title, String url, String location, String country,
        WorkMode workMode, String description) {
}
