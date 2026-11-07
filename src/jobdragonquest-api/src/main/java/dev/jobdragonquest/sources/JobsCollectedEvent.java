package dev.jobdragonquest.sources;

import java.util.List;
import java.util.UUID;

/**
 * Publicado ao fim de uma coleta que gravou vagas novas. {@code scores} traz a melhor nota de compatibilidade de cada
 * vaga nova entre os perfis ligados à fonte (null quando não há perfil ou dados para avaliar).
 */
public record JobsCollectedEvent(UUID userId, UUID sourceId, String sourceName, List<Integer> scores, UUID runId) {

    public JobsCollectedEvent {
        scores = scores == null ? List.of() : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(scores));
    }
}
