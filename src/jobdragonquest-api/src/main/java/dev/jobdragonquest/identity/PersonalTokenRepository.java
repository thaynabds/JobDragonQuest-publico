package dev.jobdragonquest.identity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PersonalTokenRepository extends JpaRepository<PersonalToken, UUID> {

    Optional<PersonalToken> findByTokenHash(String tokenHash);

    Optional<PersonalToken> findByIdAndUserId(UUID id, UUID userId);

    List<PersonalToken> findByUserIdOrderByCreatedAtDesc(UUID userId);

    @Query("select count(t) from PersonalToken t where t.userId = :userId and t.revokedAt is null and t.expiresAt > :now")
    long countActive(@Param("userId") UUID userId, @Param("now") Instant now);
}
