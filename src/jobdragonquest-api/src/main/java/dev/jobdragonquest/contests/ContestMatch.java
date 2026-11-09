package dev.jobdragonquest.contests;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Possível menção à pessoa num documento oficial. Nunca é afirmação: ela confere na fonte. */
@Entity
@Table(name = "contest_match")
class ContestMatch {

    @Id
    UUID id;

    @Column(name = "contest_id", nullable = false)
    UUID contestId;

    @Column(nullable = false, length = 2048)
    String url;

    @Column(nullable = false)
    String kind;

    @Column(nullable = false)
    String confidence;

    @Column(name = "line_text", nullable = false, length = 400)
    String line;

    @Column(name = "found_at", nullable = false)
    Instant foundAt;
}
