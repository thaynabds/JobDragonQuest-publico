package dev.jobdragonquest.documents;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoredDocumentRepository extends JpaRepository<StoredDocument, UUID> {

    Optional<StoredDocument> findByIdAndUserId(UUID id, UUID userId);

    @Query("""
            select new dev.jobdragonquest.documents.DocumentView(d.id, d.profileId, d.applicationId, d.kind, d.name, d.contentType,
                d.sizeBytes, d.version, d.createdAt)
            from StoredDocument d where d.userId = :userId and d.profileId = :profileId order by d.createdAt desc""")
    List<DocumentView> listForProfile(@Param("userId") UUID userId, @Param("profileId") UUID profileId);

    @Query("""
            select new dev.jobdragonquest.documents.DocumentView(d.id, d.profileId, d.applicationId, d.kind, d.name, d.contentType,
                d.sizeBytes, d.version, d.createdAt)
            from StoredDocument d where d.userId = :userId and d.applicationId = :applicationId order by d.createdAt desc""")
    List<DocumentView> listForApplication(@Param("userId") UUID userId, @Param("applicationId") UUID applicationId);

    long countByUserIdAndProfileId(UUID userId, UUID profileId);

    long countByUserIdAndApplicationId(UUID userId, UUID applicationId);

    @Query("select coalesce(sum(d.sizeBytes), 0) from StoredDocument d where d.userId = :userId")
    long totalBytes(@Param("userId") UUID userId);

    @Query("select coalesce(max(d.version), 0) from StoredDocument d where d.userId = :userId and d.profileId = :profileId and d.kind = :kind")
    int lastVersion(@Param("userId") UUID userId, @Param("profileId") UUID profileId, @Param("kind") StoredDocument.Kind kind);
}
