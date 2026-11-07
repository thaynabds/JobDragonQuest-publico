package dev.jobfly.notifications;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface NotificationRepository extends JpaRepository<Notification, UUID> {

    boolean existsByUserIdAndDedupeKey(UUID userId, String dedupeKey);

    Optional<Notification> findByIdAndUserId(UUID id, UUID userId);

    Page<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<Notification> findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    long countByUserIdAndReadAtIsNull(UUID userId);

    @Query("""
            select n from Notification n
            where n.userId = :userId and n.deliveredAt is null and n.readAt is null
              and (n.snoozedUntil is null or n.snoozedUntil <= :now)
            order by n.createdAt asc""")
    List<Notification> findDue(@Param("userId") UUID userId, @Param("now") Instant now, Pageable pageable);

    List<Notification> findByUserIdAndIdIn(UUID userId, Collection<UUID> ids);

    @Modifying
    @Query("update Notification n set n.readAt = :now where n.userId = :userId and n.readAt is null")
    int markAllRead(@Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying
    @Query("update Notification n set n.deliveredAt = :now where n.userId = :userId and n.deliveredAt is null")
    int markAllDelivered(@Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying
    @Query("delete from Notification n where n.userId = :userId and n.readAt is not null and n.createdAt < :before")
    int deleteReadBefore(@Param("userId") UUID userId, @Param("before") Instant before);
}
