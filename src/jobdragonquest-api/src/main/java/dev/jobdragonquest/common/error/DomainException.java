package dev.jobdragonquest.common.error;

import org.springframework.http.HttpStatus;

/**
 * Violação de regra de negócio, com código estável para o cliente traduzir ({@code code}) e status HTTP.
 * A mensagem é segura para exibir: nunca inclui dado de outro usuário nem detalhe interno.
 */
public class DomainException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public DomainException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static DomainException badRequest(String code, String message) {
        return new DomainException(HttpStatus.BAD_REQUEST, code, message);
    }

    public static DomainException conflict(String code, String message) {
        return new DomainException(HttpStatus.CONFLICT, code, message);
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }
}
