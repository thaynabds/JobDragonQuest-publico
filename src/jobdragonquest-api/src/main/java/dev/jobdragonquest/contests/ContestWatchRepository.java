package dev.jobdragonquest.contests;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ContestWatchRepository extends JpaRepository<ContestWatch, UUID> {
    List<ContestWatch> findByContestIdOrderByCreatedAt(UUID contestId);

    Optional<ContestWatch> findByIdAndContestId(UUID id, UUID contestId);

    long countByContestId(UUID contestId);
}
