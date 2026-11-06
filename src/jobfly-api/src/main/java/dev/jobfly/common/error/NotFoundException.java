package dev.jobfly.common.error;

/**
 * Recurso inexistente ou de outro usuário. As duas situações respondem igual (404), para não revelar que o recurso existe.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String resource) {
        super(resource + " não encontrado");
    }
}
