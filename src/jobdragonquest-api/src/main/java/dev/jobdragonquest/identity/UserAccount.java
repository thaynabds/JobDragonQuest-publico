package dev.jobdragonquest.identity;

import dev.jobdragonquest.common.persistence.AssignedIdEntity;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "app_user")
public class UserAccount extends AssignedIdEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false, length = 10)
    private String locale;

    @Column(name = "time_zone", nullable = false, length = 64)
    private String timeZone;

    @Column(name = "failed_logins", nullable = false)
    private int failedLogins;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserAccount() {
    }

    UserAccount(String email, String passwordHash, String displayName, String locale, String timeZone, Instant now) {
        this.id = UUID.randomUUID();
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.role = Role.USER;
        this.locale = locale;
        this.timeZone = timeZone;
        this.createdAt = now;
        this.updatedAt = now;
    }

    void rename(String newName, Instant now) {
        this.displayName = newName;
        this.updatedAt = now;
    }

    void changeEmail(String newEmail, Instant now) {
        this.email = newEmail;
        this.updatedAt = now;
    }

    void changePassword(String newHash, Instant now) {
        this.passwordHash = newHash;
        this.updatedAt = now;
    }

    int getFailedLogins() {
        return failedLogins;
    }

    Instant getLockedUntil() {
        return lockedUntil;
    }

    boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    void registerFailedLogin(Instant now, int lockoutAfter, Duration lockoutDuration) {
        failedLogins++;
        if (failedLogins >= lockoutAfter) {
            lockedUntil = now.plus(lockoutDuration);
            failedLogins = 0;
        }
        updatedAt = now;
    }

    void registerSuccessfulLogin(Instant now) {
        failedLogins = 0;
        lockedUntil = null;
        updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    String getPasswordHash() {
        return passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Role getRole() {
        return role;
    }

    public String getLocale() {
        return locale;
    }

    public String getTimeZone() {
        return timeZone;
    }
}
