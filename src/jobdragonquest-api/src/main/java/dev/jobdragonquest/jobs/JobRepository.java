package dev.jobdragonquest.jobs;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface JobRepository extends JpaRepository<JobPosting, UUID> {

    Optional<JobPosting> findByIdAndUserId(UUID id, UUID userId);

    Optional<JobPosting> findByUserIdAndUrlHash(UUID userId, String urlHash);

    List<JobPosting> findByUserIdAndCompanyNormAndTitleNorm(UUID userId, String companyNorm, String titleNorm);

    Page<JobPosting> findByUserIdOrderByFoundAtDesc(UUID userId, Pageable pageable);

    Page<JobPosting> findByUserIdAndWatchedSourceIdOrderByFoundAtDesc(UUID userId, UUID watchedSourceId, Pageable pageable);

    long countByUserId(UUID userId);
}
