package dev.jobdragonquest.contests;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ContestDocumentRepository extends JpaRepository<ContestDocument, UUID> {
    List<ContestDocument> findTop50ByContestIdOrderByReadAtDesc(UUID contestId);

    boolean existsByContestIdAndHash(UUID contestId, String hash);

    boolean existsByContestIdAndUrl(UUID contestId, String url);
}
