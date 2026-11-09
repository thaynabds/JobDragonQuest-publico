package dev.jobdragonquest.contests;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ContestRepository extends JpaRepository<Contest, UUID> {
    List<Contest> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<Contest> findByIdAndUserId(UUID id, UUID userId);

    long countByUserId(UUID userId);
}
