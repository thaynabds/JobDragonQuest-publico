package dev.jobdragonquest.documents;

import java.time.Instant;
import java.util.UUID;

/** Dados de um documento sem o conteúdo (lista). */
public record DocumentView(UUID id, UUID profileId, UUID applicationId, StoredDocument.Kind kind, String name, String contentType,
        long sizeBytes, int version, Instant createdAt) {
}
