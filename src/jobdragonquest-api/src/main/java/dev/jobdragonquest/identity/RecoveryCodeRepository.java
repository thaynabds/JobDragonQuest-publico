package dev.jobdragonquest.identity;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface RecoveryCodeRepository extends JpaRepository<RecoveryCode, UUID> {

    Optional<RecoveryCode> findByCodeHash(String codeHash);

    long countByUserIdAndUsedAtIsNull(UUID userId);

    @Modifying
    @Query("delete from RecoveryCode c where c.userId = :userId")
    int deleteAllOfUser(@Param("userId") UUID userId);
}
