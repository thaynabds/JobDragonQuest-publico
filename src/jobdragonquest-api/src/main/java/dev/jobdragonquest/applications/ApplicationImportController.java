package dev.jobdragonquest.applications;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.jobdragonquest.applications.ApplicationCsvImporter.ImportResult;
import dev.jobdragonquest.common.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/applications/import")
class ApplicationImportController {

    /** Conteúdo do arquivo CSV como texto (o painel lê o arquivo no navegador). */
    record ImportRequest(@NotBlank @Size(max = 900_000) String csv) {
    }

    private final ApplicationCsvImporter importer;
    private final CurrentUser currentUser;

    ApplicationImportController(ApplicationCsvImporter importer, CurrentUser currentUser) {
        this.importer = importer;
        this.currentUser = currentUser;
    }

    @PostMapping
    ImportResult importCsv(@Valid @RequestBody ImportRequest request) {
        return importer.importCsv(currentUser.id(), request.csv());
    }
}
