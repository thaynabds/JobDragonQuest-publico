package dev.jobfly.applications;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ApplicationRepository extends JpaRepository<JobApplication, UUID>, JpaSpecificationExecutor<JobApplication> {

    Optional<JobApplication> findByIdAndUserId(UUID id, UUID userId);

    Optional<JobApplication> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

    Optional<JobApplication> findFirstByUserIdAndUrlHashOrderByCreatedAtDesc(UUID userId, String urlHash);

    @Query("""
            select a from JobApplication a
            where a.userId = :userId and a.companyNorm = :company and a.titleNorm = :title
              and a.createdAt >= :since
            order by a.createdAt desc""")
    List<JobApplication> findRecentSameRole(@Param("userId") UUID userId, @Param("company") String company,
            @Param("title") String title, @Param("since") Instant since);

    List<JobApplication> findByUserId(UUID userId);
}
