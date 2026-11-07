package dev.jobdragonquest.identity;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.jobdragonquest.common.security.CurrentUser;
import dev.jobdragonquest.identity.PersonalTokenService.CreateRequest;
import dev.jobdragonquest.identity.PersonalTokenService.CreatedToken;
import dev.jobdragonquest.identity.PersonalTokenService.TokenView;

/** Gerência de tokens pessoais. Só com login no painel: um token não gerencia tokens. */
@RestController
@RequestMapping("/api/v1/tokens")
class PersonalTokenController {

    private final PersonalTokenService tokens;
    private final CurrentUser currentUser;

    PersonalTokenController(PersonalTokenService tokens, CurrentUser currentUser) {
        this.tokens = tokens;
        this.currentUser = currentUser;
    }

    @GetMapping
    List<TokenView> list() {
        return tokens.list(currentUser.requireSession());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CreatedToken create(@RequestBody CreateRequest request) {
        return tokens.create(currentUser.requireSession(), request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revoke(@PathVariable UUID id) {
        tokens.revoke(currentUser.requireSession(), id);
    }
}
