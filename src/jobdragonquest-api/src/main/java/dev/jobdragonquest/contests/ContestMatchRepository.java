package dev.jobdragonquest.contests;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ContestMatchRepository extends JpaRepository<ContestMatch, UUID> {
    List<ContestMatch> findTop100ByContestIdOrderByFoundAtDesc(UUID contestId);
}
