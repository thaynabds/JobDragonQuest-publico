package dev.jobdragonquest.contests;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Concurso acompanhado por uma pessoa. As etapas ficam em JSON validado pela API. */
@Entity
@Table(name = "contest")
class Contest {

    @Id
    UUID id;

    @Column(name = "user_id", nullable = false)
    UUID userId;

    @Column(nullable = false)
    String name;

    String organ;
    String board;
    String notice;

    @Column(name = "role_name")
    String roleName;

    @Column(name = "state_code")
    String stateCode;

    String city;
    String registration;

    @Column(name = "search_name")
    String searchName;

    @Column(name = "valid_until")
    LocalDate validUntil;

    @Column(columnDefinition = "text")
    String notes;

    @Column(nullable = false, columnDefinition = "text")
    String stages;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;
}
