package dev.jobdragonquest.common.web;

import java.io.IOException;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import dev.jobdragonquest.common.error.ProblemResponses;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Recusa corpos acima de 1 MiB antes de ler o conteúdo. Sem isto, um JSON gigante ocuparia memória do servidor.
 * Pedidos sem Content-Length (chunked) também são recusados nos métodos que levam corpo: a API não precisa deles.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    static final long MAX_BODY_BYTES = 1024L * 1024L;
    /** Envio de documentos (multipart): até 6 MiB, só nestas rotas. */
    static final long MAX_UPLOAD_BYTES = 6L * 1024L * 1024L;
    static final Pattern UPLOAD_PATHS = Pattern.compile(
            "/api/v1/(profiles/[0-9a-fA-F-]{36}/documents|applications/[0-9a-fA-F-]{36}/documents|documents/read)");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean hasBody = switch (request.getMethod()) {
            case "POST", "PUT", "PATCH" -> true;
            default -> false;
        };
        long length = request.getContentLengthLong();
        boolean chunked = "chunked".equalsIgnoreCase(request.getHeader("Transfer-Encoding"));
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String type = request.getContentType() == null ? "" : request.getContentType().toLowerCase(Locale.ROOT);
        long limit = type.startsWith("multipart/form-data") && UPLOAD_PATHS.matcher(path).matches() ? MAX_UPLOAD_BYTES : MAX_BODY_BYTES;
        if (hasBody && (length > limit || (length < 0 && chunked))) {
            ProblemResponses.write(request, response, HttpStatus.CONTENT_TOO_LARGE, "payload_too_large", "Corpo acima do limite");
            return;
        }
        chain.doFilter(request, response);
    }
}
