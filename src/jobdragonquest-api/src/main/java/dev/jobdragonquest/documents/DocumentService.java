package dev.jobdragonquest.documents;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import dev.jobdragonquest.applications.ApplicationService;
import dev.jobdragonquest.audit.AuditService;
import dev.jobdragonquest.common.error.DomainException;
import dev.jobdragonquest.common.error.NotFoundException;
import dev.jobdragonquest.jobs.link.SafeWebFetcher;
import dev.jobdragonquest.profiles.ProfileService;
import dev.jobdragonquest.sources.collect.SkillCatalog;

/**
 * Documentos do perfil (currículos com versão, cartas, certificados) e anexos das candidaturas, mais a leitura de
 * arquivos e links para propor itens ao perfil (issue #124). Cada documento é só da conta dona; outra conta recebe 404.
 * A auditoria guarda o id, nunca o nome do arquivo nem o conteúdo.
 */
@Service
public class DocumentService {

    static final int MAX_BYTES = 5 * 1024 * 1024;
    static final long MAX_ACCOUNT_BYTES = 200L * 1024 * 1024;
    static final int MAX_PER_PROFILE = 50;
    static final int MAX_PER_APPLICATION = 10;

    /** Resultado da leitura de um arquivo ou link: texto extraído e proposta de item para o perfil. */
    public record Reading(String type, String text, boolean ocrUnavailable, CertificateParser.Proposal proposal, ResumeParser.Proposal resume) {
    }

    /** Conteúdo para download, com o tipo identificado pelo próprio arquivo. */
    public record Download(String name, String contentType, byte[] data) {
    }

    private final StoredDocumentRepository documents;
    private final ProfileService profiles;
    private final ApplicationService applications;
    private final SafeWebFetcher fetcher;
    private final SkillCatalog skills;
    private final AuditService audit;
    private final Clock clock;

    DocumentService(StoredDocumentRepository documents, ProfileService profiles, ApplicationService applications, SafeWebFetcher fetcher,
            SkillCatalog skills, AuditService audit, Clock clock) {
        this.documents = documents;
        this.profiles = profiles;
        this.applications = applications;
        this.fetcher = fetcher;
        this.skills = skills;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public DocumentView saveForProfile(UUID userId, UUID profileId, StoredDocument.Kind kind, String fileName, byte[] data) {
        profiles.requireOwned(userId, profileId);
        if (kind == StoredDocument.Kind.ATTACHMENT) {
            throw DomainException.badRequest("document_kind", "Anexo é para candidaturas.");
        }
        if (documents.countByUserIdAndProfileId(userId, profileId) >= MAX_PER_PROFILE) {
            throw DomainException.conflict("document_limit", "Cada perfil guarda até " + MAX_PER_PROFILE + " documentos.");
        }
        int version = documents.lastVersion(userId, profileId, kind) + 1;
        return save(userId, profileId, null, kind, fileName, data, version);
    }

    @Transactional
    public DocumentView saveForApplication(UUID userId, UUID applicationId, String fileName, byte[] data) {
        applications.requireOwned(userId, applicationId);
        if (documents.countByUserIdAndApplicationId(userId, applicationId) >= MAX_PER_APPLICATION) {
            throw DomainException.conflict("document_limit", "Cada candidatura guarda até " + MAX_PER_APPLICATION + " anexos.");
        }
        return save(userId, null, applicationId, StoredDocument.Kind.ATTACHMENT, fileName, data, 1);
    }

    @Transactional(readOnly = true)
    public List<DocumentView> listForProfile(UUID userId, UUID profileId) {
        profiles.requireOwned(userId, profileId);
        return documents.listForProfile(userId, profileId);
    }

    @Transactional(readOnly = true)
    public List<DocumentView> listForApplication(UUID userId, UUID applicationId) {
        applications.requireOwned(userId, applicationId);
        return documents.listForApplication(userId, applicationId);
    }

    @Transactional(readOnly = true)
    public Download download(UUID userId, UUID documentId) {
        StoredDocument document = load(userId, documentId);
        return new Download(document.getName(), document.getContentType(), document.getData());
    }

    @Transactional
    public void delete(UUID userId, UUID documentId) {
        StoredDocument document = load(userId, documentId);
        documents.delete(document);
        audit.record(userId, "document.deleted", "document", documentId, null);
    }

    /** Texto e proposta de um documento já guardado. */
    @Transactional(readOnly = true)
    public Reading readStored(UUID userId, UUID documentId) {
        StoredDocument document = load(userId, documentId);
        String text = document.getExtractedText() == null ? "" : document.getExtractedText();
        return reading(DocumentText.detect(document.getData(), document.getName()), text);
    }

    /** Lê um arquivo sem guardar: para conferir um certificado ou um currículo antigo antes de passar para o perfil. */
    public Reading read(String fileName, byte[] data) {
        checkSize(data);
        DocumentText.Type type = DocumentText.detect(data, fileName);
        return reading(type, DocumentText.extract(type, data));
    }

    /** Lê a página de um link (certificado, curso, credencial) com a mesma proteção contra SSRF das vagas. */
    public Reading readLink(String url) {
        URI uri;
        try {
            uri = new URI(url.strip());
        } catch (URISyntaxException ex) {
            throw DomainException.badRequest("invalid_url", "Link inválido.");
        }
        String html = fetcher.fetchHtml(uri);
        String text = DocumentText.clean(HtmlUtils.htmlUnescape(html
                .replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ")
                .replaceAll("(?i)<\\s*(br|/p|/li|/h[1-6]|/div|/tr)\\s*/?>", "\n")
                .replaceAll("<[^>]*>", " ")));
        CertificateParser.Proposal proposal = CertificateParser.parse(text, List.copyOf(skills.detect(text)));
        if (proposal.url() == null) {
            proposal = new CertificateParser.Proposal(proposal.kind(), proposal.title(), proposal.organization(), proposal.date(),
                    proposal.hours(), proposal.credentialId(), uri.toString(), proposal.skills());
        }
        return new Reading("LINK", text, false, proposal, null);
    }

    private Reading reading(DocumentText.Type type, String text) {
        boolean ocrUnavailable = type.isImage() && text.isBlank();
        return new Reading(type.name(), text, ocrUnavailable, CertificateParser.parse(text, List.copyOf(skills.detect(text))), ResumeParser.parse(text));
    }

    private DocumentView save(UUID userId, UUID profileId, UUID applicationId, StoredDocument.Kind kind, String fileName, byte[] data,
            int version) {
        checkSize(data);
        if (documents.totalBytes(userId) + data.length > MAX_ACCOUNT_BYTES) {
            throw DomainException.conflict("document_quota", "Os documentos da conta passariam de 200 MB. Apague alguns antes.");
        }
        DocumentText.Type type = DocumentText.detect(data, fileName);
        String text = DocumentText.extract(type, data);
        StoredDocument document = documents.save(new StoredDocument(userId, profileId, applicationId, kind, safeName(fileName, type),
                type.contentType, data, sha256(data), version, text.isBlank() ? null : text, clock.instant()));
        audit.record(userId, "document.saved", "document", document.getId(), kind.name());
        return new DocumentView(document.getId(), profileId, applicationId, kind, document.getName(), document.getContentType(),
                document.getSizeBytes(), version, document.getCreatedAt());
    }

    private StoredDocument load(UUID userId, UUID documentId) {
        return documents.findByIdAndUserId(documentId, userId).orElseThrow(() -> new NotFoundException("Documento"));
    }

    private static void checkSize(byte[] data) {
        if (data == null || data.length == 0) {
            throw DomainException.badRequest("document_empty", "O arquivo está vazio.");
        }
        if (data.length > MAX_BYTES) {
            throw DomainException.badRequest("document_too_large", "O arquivo passa de 5 MB.");
        }
    }

    /**
     * Nome seguro para mostrar e baixar: só a parte final (sem pastas), sem caracteres de controle nem aspas, até 120
     * caracteres e com a extensão do tipo real.
     */
    static String safeName(String original, DocumentText.Type type) {
        String base = original == null ? "documento" : original.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"<>|:*?]", "").strip();
        base = base.replaceFirst("\\.[A-Za-z0-9]{1,5}$", "");
        if (base.isBlank() || base.startsWith(".")) {
            base = "documento";
        }
        String extension = switch (type) {
            case PDF -> ".pdf";
            case DOCX -> ".docx";
            case ODT -> ".odt";
            case XLSX -> ".xlsx";
            case ODS -> ".ods";
            case TEXT -> ".txt";
            case PNG -> ".png";
            case JPEG -> ".jpg";
        };
        return (base.length() > 120 ? base.substring(0, 120) : base) + extension;
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
