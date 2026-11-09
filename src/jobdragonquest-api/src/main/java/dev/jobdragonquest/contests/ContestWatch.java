package dev.jobdragonquest.contests;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Página oficial vigiada. Status: NEW, OK, BLOCKED (robots.txt) ou FAILED. */
@Entity
@Table(name = "contest_watch")
class ContestWatch {

    @Id
    UUID id;

    @Column(name = "contest_id", nullable = false)
    UUID contestId;

    @Column(nullable = false, length = 2048)
    String url;

    String label;

    @Column(name = "last_hash")
    String lastHash;

    @Column(name = "last_checked_at")
    Instant lastCheckedAt;

    @Column(nullable = false)
    String status;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;
}
