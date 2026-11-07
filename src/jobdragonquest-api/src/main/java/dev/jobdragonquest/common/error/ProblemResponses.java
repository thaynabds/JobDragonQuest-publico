package dev.jobdragonquest.common.error;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Respostas de erro escritas fora dos controllers (filtros e segurança) no mesmo formato Problem Details (RFC 9457) que
 * o {@link GlobalExceptionHandler}: {@code title}, {@code status}, {@code detail}, {@code instance} e {@code code}. Como no
 * Spring, {@code type} fica ausente, o que pela RFC 9457 equivale a {@code about:blank}. Os textos são fixos do código; só o
 * caminho em {@code instance} vem da requisição, e por isso todo valor é escapado.
 */
public final class ProblemResponses {

    private ProblemResponses() {
    }

    public static void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String code, String detail)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        String path = request.getRequestURI() == null ? "" : request.getRequestURI();
        response.getWriter().write("{\"detail\":\"" + json(detail) + "\",\"instance\":\"" + json(path.length() > 512 ? path.substring(0, 512) : path)
                + "\",\"status\":" + status.value() + ",\"title\":\"" + json(status.getReasonPhrase()) + "\",\"code\":\"" + json(code) + "\"}");
    }

    /** 401 com o cabeçalho {@code WWW-Authenticate: Bearer} da RFC 6750 e o corpo padrão, sem dizer por que o token falhou. */
    public static AuthenticationEntryPoint unauthorized() {
        BearerTokenAuthenticationEntryPoint bearer = new BearerTokenAuthenticationEntryPoint();
        return (request, response, exception) -> {
            bearer.commence(request, response, exception);
            if (response.getHeader(HttpHeaders.WWW_AUTHENTICATE) == null) {
                response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            }
            write(request, response, HttpStatus.UNAUTHORIZED, "unauthorized", "Autenticação necessária");
        };
    }

    public static AccessDeniedHandler forbidden() {
        return (request, response, exception) -> write(request, response, HttpStatus.FORBIDDEN, "forbidden", "Acesso negado");
    }

    static String json(String value) {
        StringBuilder out = new StringBuilder(value.length() + 8);
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '<' -> out.append("\\u003c");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.toString();
    }
}
