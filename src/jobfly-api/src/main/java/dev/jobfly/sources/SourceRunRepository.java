package dev.jobfly.sources;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface SourceRunRepository extends JpaRepository<SourceRun, UUID> {

    List<SourceRun> findBySourceIdOrderByStartedAtDesc(UUID sourceId, Pageable page);
}
