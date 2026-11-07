package dev.jobdragonquest.sources;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import dev.jobdragonquest.sources.SourceKind.CollectionMode;

interface WatchedSourceRepository extends JpaRepository<WatchedSource, UUID> {

    List<WatchedSource> findByUserIdOrderByNameAsc(UUID userId);

    Optional<WatchedSource> findByIdAndUserId(UUID id, UUID userId);

    List<WatchedSource> findByUserIdAndIdIn(UUID userId, Collection<UUID> ids);

    Optional<WatchedSource> findByUserIdAndDedupKey(UUID userId, String dedupKey);

    long countByUserId(UUID userId);

    @Query("""
            select s.id from WatchedSource s
            where s.enabled = true and s.collectionMode = :mode and s.nextRunAt <= :now
              and (s.runningUntil is null or s.runningUntil < :now)
            order by s.nextRunAt asc""")
    List<UUID> findDueIds(@Param("mode") CollectionMode mode, @Param("now") Instant now, Pageable page);

    /**
     * Reserva a fonte para uma coleta (manual ou agendada). Troca condicional: se duas tentativas chegarem juntas, só
     * uma recebe 1. Uma reserva vencida (coleta interrompida) pode ser tomada de novo.
     */
    @Modifying
    @Query("""
            update WatchedSource s set s.runningUntil = :until
            where s.id = :id and (s.runningUntil is null or s.runningUntil < :now)""")
    int reserve(@Param("id") UUID id, @Param("now") Instant now, @Param("until") Instant until);

    @Modifying
    @Query("update WatchedSource s set s.runningUntil = null where s.id = :id")
    int release(@Param("id") UUID id);
}
