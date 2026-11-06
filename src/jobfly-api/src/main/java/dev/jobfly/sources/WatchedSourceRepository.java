package dev.jobfly.sources;

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

import dev.jobfly.sources.SourceKind.CollectionMode;

interface WatchedSourceRepository extends JpaRepository<WatchedSource, UUID> {

    List<WatchedSource> findByUserIdOrderByNameAsc(UUID userId);

    Optional<WatchedSource> findByIdAndUserId(UUID id, UUID userId);

    List<WatchedSource> findByUserIdAndIdIn(UUID userId, Collection<UUID> ids);

    Optional<WatchedSource> findByUserIdAndDedupKey(UUID userId, String dedupKey);

    long countByUserId(UUID userId);

    @Query("""
            select s.id from WatchedSource s
            where s.enabled = true and s.collectionMode = :mode and s.nextRunAt <= :now
            order by s.nextRunAt asc""")
    List<UUID> findDueIds(@Param("mode") CollectionMode mode, @Param("now") Instant now, Pageable page);

    /**
     * Reserva a fonte para uma coleta: só uma execução vence quando duas tentam ao mesmo tempo (troca condicional
     * do próximo horário, sem depender de SKIP LOCKED).
     */
    @Modifying
    @Query("""
            update WatchedSource s set s.nextRunAt = :lease
            where s.id = :id and s.enabled = true and s.nextRunAt <= :now""")
    int claim(@Param("id") UUID id, @Param("now") Instant now, @Param("lease") Instant lease);
}
