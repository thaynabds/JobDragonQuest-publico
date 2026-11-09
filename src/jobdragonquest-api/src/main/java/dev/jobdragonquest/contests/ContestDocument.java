package dev.jobdragonquest.contests;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Documento já processado, pelo hash do conteúdo. Status: READ, NO_TEXT ou FAILED. */
@Entity
@Table(name = "contest_document")
class ContestDocument {

    @Id
    UUID id;

    @Column(name = "contest_id", nullable = false)
    UUID contestId;

    @Column(nullable = false, length = 2048)
    String url;

    @Column(nullable = false)
    String hash;

    @Column(nullable = false)
    String status;

    @Column(name = "read_at", nullable = false)
    Instant readAt;
}
