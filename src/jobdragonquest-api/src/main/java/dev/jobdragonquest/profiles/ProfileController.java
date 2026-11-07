package dev.jobdragonquest.profiles;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.jobdragonquest.common.security.CurrentUser;
import dev.jobdragonquest.profiles.ProfileDtos.EntryDto;
import dev.jobdragonquest.profiles.ProfileDtos.ProfileRequest;
import dev.jobdragonquest.profiles.ProfileDtos.ProfileResponse;
import dev.jobdragonquest.profiles.ProfileDtos.ProfileSummary;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/profiles")
class ProfileController {

    private final ProfileService profiles;
    private final CurrentUser currentUser;

    ProfileController(ProfileService profiles, CurrentUser currentUser) {
        this.profiles = profiles;
        this.currentUser = currentUser;
    }

    @GetMapping
    List<ProfileSummary> list() {
        return profiles.list(currentUser.id());
    }

    @GetMapping("/{id}")
    ProfileResponse get(@PathVariable UUID id) {
        return profiles.get(currentUser.id(), id);
    }

    @PostMapping
    ResponseEntity<ProfileResponse> create(@Valid @RequestBody ProfileRequest request) {
        ProfileResponse profile = profiles.create(currentUser.id(), request);
        return ResponseEntity.created(URI.create("/api/v1/profiles/" + profile.id())).body(profile);
    }

    @PostMapping("/{id}/entries")
    ProfileResponse appendEntry(@PathVariable UUID id, @Valid @RequestBody EntryDto entry) {
        return profiles.appendEntry(currentUser.id(), id, entry);
    }

    @PutMapping("/{id}")
    ProfileResponse update(@PathVariable UUID id, @Valid @RequestBody ProfileRequest request) {
        return profiles.update(currentUser.id(), id, request);
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable UUID id) {
        profiles.delete(currentUser.id(), id);
        return ResponseEntity.noContent().build();
    }
}
