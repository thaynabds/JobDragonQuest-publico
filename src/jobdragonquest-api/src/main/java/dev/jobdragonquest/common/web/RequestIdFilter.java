package dev.jobdragonquest.common.web;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Dá a cada requisição um identificador ({@code X-Request-Id}) que aparece em todas as linhas de log dela (MDC
 * {@code requestId}) e volta no cabeçalho da resposta, para ligar uma reclamação ao log sem expor dado pessoal.
 * Um identificador recebido só é aceito se for curto e só com letras, números e hífen; senão é trocado por um UUID
 * (evita injeção de linha no log).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";
    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9-]{8,64}");

    static String resolve(String incoming) {
        return incoming != null && VALID.matcher(incoming).matches() ? incoming : UUID.randomUUID().toString();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String id = resolve(request.getHeader(HEADER));
        MDC.put(MDC_KEY, id);
        response.setHeader(HEADER, id);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
