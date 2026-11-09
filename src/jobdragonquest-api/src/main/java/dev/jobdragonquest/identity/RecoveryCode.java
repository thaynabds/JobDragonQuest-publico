package dev.jobdragonquest.identity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Código de recuperação da conta, guardado só como hash e válido uma vez. */
@Entity
@Table(name = "recovery_code")
class RecoveryCode {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RecoveryCode() {
    }

    RecoveryCode(UUID userId, String codeHash, Instant now) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.codeHash = codeHash;
        this.createdAt = now;
    }

    UUID getUserId() {
        return userId;
    }

    boolean isUsed() {
        return usedAt != null;
    }

    void use(Instant now) {
        this.usedAt = now;
    }
}
