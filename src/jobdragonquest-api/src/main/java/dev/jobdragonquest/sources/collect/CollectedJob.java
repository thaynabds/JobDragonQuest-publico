package dev.jobdragonquest.sources.collect;

import dev.jobdragonquest.common.domain.WorkMode;

/** Vaga como veio do ATS, antes de virar uma vaga do JobDragonQuest. Campos opcionais podem ser null. */
public record CollectedJob(String externalId, String title, String url, String location, String country,
        WorkMode workMode, String description) {
}
