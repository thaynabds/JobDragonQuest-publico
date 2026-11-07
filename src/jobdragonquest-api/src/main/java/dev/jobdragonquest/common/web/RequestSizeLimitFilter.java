package dev.jobdragonquest.common.web;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean hasBody = switch (request.getMethod()) {
            case "POST", "PUT", "PATCH" -> true;
            default -> false;
        };
        long length = request.getContentLengthLong();
        boolean chunked = "chunked".equalsIgnoreCase(request.getHeader("Transfer-Encoding"));
        if (hasBody && (length > MAX_BODY_BYTES || (length < 0 && chunked))) {
            response.setStatus(HttpStatus.CONTENT_TOO_LARGE.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write("{\"status\":413,\"code\":\"payload_too_large\",\"detail\":\"Corpo acima de 1 MiB\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
