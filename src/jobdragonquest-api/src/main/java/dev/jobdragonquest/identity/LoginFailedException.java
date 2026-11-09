package dev.jobdragonquest.identity;

import org.springframework.http.HttpStatus;

import dev.jobdragonquest.common.error.DomainException;

/** Login recusado: leva quantas tentativas restam ou por quantos minutos a conta ficou bloqueada. */
public class LoginFailedException extends DomainException {

    private final Integer attemptsLeft;
    private final Long retryAfterMinutes;

    LoginFailedException(HttpStatus status, String code, String message, Integer attemptsLeft, Long retryAfterMinutes) {
        super(status, code, message);
        this.attemptsLeft = attemptsLeft;
        this.retryAfterMinutes = retryAfterMinutes;
    }

    public Integer attemptsLeft() {
        return attemptsLeft;
    }

    public Long retryAfterMinutes() {
        return retryAfterMinutes;
    }
}
