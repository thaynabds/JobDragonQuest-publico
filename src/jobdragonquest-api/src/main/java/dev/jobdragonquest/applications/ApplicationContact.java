package dev.jobdragonquest.applications;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import dev.jobdragonquest.common.persistence.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Pessoa de contato num processo seletivo. Dado de terceiro: só a conta dona da candidatura vê. */
@Entity
@Table(name = "application_contact")
public class ApplicationContact extends AssignedIdEntity {

    /** Papel da pessoa no processo. */
    public enum Role {
        RECRUITER,
        HIRING_MANAGER,
        INTERVIEWER,
        REFERRAL,
        OTHER
    }

    @Id
    private UUID id;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 120)
    private String position;

    @Column(length = 254)
    private String email;

    @Column(name = "linkedin_url", length = 500)
    private String linkedinUrl;

    @Column(length = 2000)
    private String notes;

    @Column(name = "contacted_at")
    private Instant contactedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ApplicationContact() {
    }

    ApplicationContact(UUID applicationId, Role role, String name, String position, String email, String linkedinUrl,
            String notes, Instant contactedAt, Instant now) {
        this.id = UUID.randomUUID();
        this.applicationId = applicationId;
        this.role = role;
        this.name = name;
        this.position = position;
        this.email = email;
        this.linkedinUrl = linkedinUrl;
        this.notes = notes;
        this.contactedAt = contactedAt;
        this.createdAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getApplicationId() {
        return applicationId;
    }

    public Role getRole() {
        return role;
    }

    public String getName() {
        return name;
    }

    public String getPosition() {
        return position;
    }

    public String getEmail() {
        return email;
    }

    public String getLinkedinUrl() {
        return linkedinUrl;
    }

    public String getNotes() {
        return notes;
    }

    public Instant getContactedAt() {
        return contactedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
