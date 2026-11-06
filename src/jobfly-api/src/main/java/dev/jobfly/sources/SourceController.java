package dev.jobfly.sources;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.jobfly.common.security.CurrentUser;
import dev.jobfly.sources.SourceDtos.BulkCreateRequest;
import dev.jobfly.sources.SourceDtos.BulkCreateResult;
import dev.jobfly.sources.SourceDtos.BulkUpdateRequest;
import dev.jobfly.sources.SourceDtos.CatalogEntry;
import dev.jobfly.sources.SourceDtos.RunResponse;
import dev.jobfly.sources.SourceDtos.SourceResponse;
import dev.jobfly.sources.SourceDtos.UpdateSourceRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/sources")
class SourceController {

    private final SourceService sources;
    private final SourceCollector collector;
    private final CurrentUser currentUser;

    SourceController(SourceService sources, SourceCollector collector, CurrentUser currentUser) {
        this.sources = sources;
        this.collector = collector;
        this.currentUser = currentUser;
    }

    /** Tipos de fonte suportados e como cada um é vigiado (automático ou assistido). */
    @GetMapping("/catalog")
    List<CatalogEntry> catalog() {
        return sources.catalog();
    }

    @GetMapping
    List<SourceResponse> list(@RequestParam(required = false) UUID profileId) {
        return sources.list(currentUser.id(), profileId);
    }

    @GetMapping("/{id}")
    SourceResponse get(@PathVariable UUID id) {
        return sources.get(currentUser.id(), id);
    }

    /** Cadastra várias fontes de uma vez, ligadas aos mesmos perfis. Duplicadas voltam em {@code duplicates}. */
    @PostMapping("/bulk")
    ResponseEntity<BulkCreateResult> createBulk(@Valid @RequestBody BulkCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sources.createBulk(currentUser.id(), request));
    }

    @PostMapping("/bulk-update")
    List<SourceResponse> updateBulk(@Valid @RequestBody BulkUpdateRequest request) {
        return sources.updateBulk(currentUser.id(), request);
    }

    @PutMapping("/{id}")
    SourceResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateSourceRequest request) {
        return sources.update(currentUser.id(), id, request);
    }

    /** Coleta agora uma fonte automática. Fontes assistidas respondem 409 (não há coleta automática permitida). */
    @PostMapping("/{id}/run")
    RunResponse run(@PathVariable UUID id) {
        return collector.runNow(currentUser.id(), id);
    }

    @GetMapping("/{id}/runs")
    List<RunResponse> runs(@PathVariable UUID id) {
        return sources.runs(currentUser.id(), id);
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable UUID id) {
        sources.delete(currentUser.id(), id);
        return ResponseEntity.noContent().build();
    }
}
