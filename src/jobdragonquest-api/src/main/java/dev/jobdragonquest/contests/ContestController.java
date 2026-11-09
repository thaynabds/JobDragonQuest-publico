package dev.jobdragonquest.contests;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.jobdragonquest.common.security.CurrentUser;
import dev.jobdragonquest.contests.ContestDtos.ContestRequest;
import dev.jobdragonquest.contests.ContestDtos.ContestResponse;
import dev.jobdragonquest.contests.ContestDtos.ScanResult;
import dev.jobdragonquest.contests.ContestDtos.WatchRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/contests")
class ContestController {

    private final ContestService service;
    private final ContestScanner scanner;
    private final CurrentUser currentUser;

    ContestController(ContestService service, ContestScanner scanner, CurrentUser currentUser) {
        this.service = service;
        this.scanner = scanner;
        this.currentUser = currentUser;
    }

    @GetMapping
    List<ContestResponse> list() {
        return service.list(currentUser.id());
    }

    @GetMapping("/{id}")
    ContestResponse get(@PathVariable UUID id) {
        return service.get(currentUser.id(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ContestResponse create(@Valid @RequestBody ContestRequest request) {
        return service.create(currentUser.id(), request);
    }

    @PutMapping("/{id}")
    ContestResponse update(@PathVariable UUID id, @Valid @RequestBody ContestRequest request) {
        return service.update(currentUser.id(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) {
        service.delete(currentUser.id(), id);
    }

    @PostMapping("/{id}/watches")
    @ResponseStatus(HttpStatus.CREATED)
    ContestResponse addWatch(@PathVariable UUID id, @Valid @RequestBody WatchRequest request) {
        return service.addWatch(currentUser.id(), id, request);
    }

    @DeleteMapping("/{id}/watches/{watchId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteWatch(@PathVariable UUID id, @PathVariable UUID watchId) {
        service.deleteWatch(currentUser.id(), id, watchId);
    }

    @PostMapping("/{id}/scan")
    ScanResult scan(@PathVariable UUID id) {
        return scanner.scan(currentUser.id(), id);
    }
}
