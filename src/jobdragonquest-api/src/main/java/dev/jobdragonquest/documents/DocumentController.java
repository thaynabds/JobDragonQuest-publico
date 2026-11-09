package dev.jobdragonquest.documents;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import dev.jobdragonquest.common.error.DomainException;
import dev.jobdragonquest.common.security.CurrentUser;
import dev.jobdragonquest.documents.DocumentService.Download;
import dev.jobdragonquest.documents.DocumentService.Reading;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
class DocumentController {

    record LinkRequest(@NotBlank @Size(max = 2048) String url) {
    }

    private final DocumentService documents;
    private final CurrentUser currentUser;

    DocumentController(DocumentService documents, CurrentUser currentUser) {
        this.documents = documents;
        this.currentUser = currentUser;
    }

    @PostMapping(path = "/api/v1/profiles/{profileId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<DocumentView> saveForProfile(@PathVariable UUID profileId, @RequestParam StoredDocument.Kind kind,
            @RequestParam MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(documents.saveForProfile(currentUser.id(), profileId, kind, file.getOriginalFilename(), bytes(file)));
    }

    @GetMapping("/api/v1/profiles/{profileId}/documents")
    List<DocumentView> listForProfile(@PathVariable UUID profileId) {
        return documents.listForProfile(currentUser.id(), profileId);
    }

    @PostMapping(path = "/api/v1/applications/{applicationId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<DocumentView> saveForApplication(@PathVariable UUID applicationId, @RequestParam MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(documents.saveForApplication(currentUser.id(), applicationId, file.getOriginalFilename(), bytes(file)));
    }

    @GetMapping("/api/v1/applications/{applicationId}/documents")
    List<DocumentView> listForApplication(@PathVariable UUID applicationId) {
        return documents.listForApplication(currentUser.id(), applicationId);
    }

    /**
     * Baixa sempre como anexo, com o tipo identificado pelo conteúdo, nosniff e CSP que bloqueia tudo: um arquivo
     * enviado nunca é executado pelo navegador como página.
     */
    @GetMapping("/api/v1/documents/{documentId}/download")
    ResponseEntity<byte[]> download(@PathVariable UUID documentId) {
        requireSessionIfSensitive(documentId);
        Download download = documents.download(currentUser.id(), documentId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(download.name(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .contentType(MediaType.parseMediaType(download.contentType()))
                .body(download.data());
    }

    @DeleteMapping("/api/v1/documents/{documentId}")
    ResponseEntity<Void> delete(@PathVariable UUID documentId) {
        documents.delete(currentUser.id(), documentId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/documents/{documentId}/read")
    Reading readStored(@PathVariable UUID documentId) {
        requireSessionIfSensitive(documentId);
        return documents.readStored(currentUser.id(), documentId);
    }

    private void requireSessionIfSensitive(UUID documentId) {
        if (documents.isSensitive(currentUser.id(), documentId)) {
            currentUser.requireSession();
        }
    }

    /** Lê um arquivo sem guardar e devolve o texto e a proposta para o perfil. */
    @PostMapping(path = "/api/v1/documents/read", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    Reading read(@RequestParam MultipartFile file) {
        currentUser.id();
        return documents.read(file.getOriginalFilename(), bytes(file));
    }

    @PostMapping("/api/v1/documents/read-link")
    Reading readLink(@Valid @RequestBody LinkRequest request) {
        currentUser.id();
        return documents.readLink(request.url());
    }

    private static byte[] bytes(MultipartFile file) {
        if (file.getSize() > DocumentService.MAX_BYTES) {
            throw DomainException.badRequest("document_too_large", "O arquivo passa de 5 MB.");
        }
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw DomainException.badRequest("document_unreadable", "Não foi possível ler o arquivo enviado.");
        }
    }
}
