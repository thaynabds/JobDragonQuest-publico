package dev.jobdragonquest.settings;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Preferência da conta guardada como JSON já validado. */
@Entity
@Table(name = "account_setting")
public class AccountSetting {

    @Embeddable
    public record Key(@Column(name = "user_id") UUID userId, @Column(name = "setting_key", length = 40) String settingKey) implements Serializable {
    }

    @EmbeddedId
    private Key id;

    @Column(name = "setting_value", nullable = false, length = 4000)
    private String value;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AccountSetting() {
    }

    AccountSetting(Key id, String value, Instant now) {
        this.id = id;
        this.value = value;
        this.updatedAt = now;
    }

    void change(String value, Instant now) {
        this.value = value;
        this.updatedAt = now;
    }

    public String getValue() {
        return value;
    }
}
