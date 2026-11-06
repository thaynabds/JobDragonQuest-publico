package dev.jobfly.sources.collect;

/** Falha de coleta com mensagem segura para mostrar no painel (sem corpo de resposta nem detalhe interno). */
public class CollectionException extends RuntimeException {

    public CollectionException(String message) {
        super(message);
    }

    public CollectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
