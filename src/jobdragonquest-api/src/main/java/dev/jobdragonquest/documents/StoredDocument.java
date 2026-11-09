package dev.jobdragonquest.documents;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import dev.jobdragonquest.common.persistence.AssignedIdEntity;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Arquivo guardado no banco local, ligado a um perfil (currículos, cartas, certificados) ou a uma candidatura (anexo). */
@Entity
@Table(name = "stored_document")
public class StoredDocument extends AssignedIdEntity {

    public enum Kind {
        RESUME,
        COVER_LETTER,
        CERTIFICATE,
        ATTACHMENT,
        OTHER,
        DIPLOMA,
        TRANSCRIPT,
        /** Dado de saúde: o texto não é extraído e token de integração não abre o arquivo. */
        MEDICAL_REPORT
    }

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "profile_id")
    private UUID profileId;

    @Column(name = "application_id")
    private UUID applicationId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Kind kind;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(nullable = false)
    private int version;

    @Basic(fetch = FetchType.LAZY)
    @Column(nullable = false)
    private byte[] data;

    @Column(name = "extracted_text", length = 100_000)
    private String extractedText;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected StoredDocument() {
    }

    StoredDocument(UUID userId, UUID profileId, UUID applicationId, Kind kind, String name, String contentType, byte[] data,
            String sha256, int version, String extractedText, Instant now) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.profileId = profileId;
        this.applicationId = applicationId;
        this.kind = kind;
        this.name = name;
        this.contentType = contentType;
        this.sizeBytes = data.length;
        this.data = data;
        this.sha256 = sha256;
        this.version = version;
        this.extractedText = extractedText;
        this.createdAt = now;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getProfileId() { return profileId; }
    public UUID getApplicationId() { return applicationId; }
    public Kind getKind() { return kind; }
    public String getName() { return name; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public String getSha256() { return sha256; }
    public int getVersion() { return version; }
    public byte[] getData() { return data; }
    public String getExtractedText() { return extractedText; }
    public Instant getCreatedAt() { return createdAt; }
}
