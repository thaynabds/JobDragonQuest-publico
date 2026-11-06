package dev.jobfly.sources.collect;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * GET em HTTPS só para os hosts dos ATS suportados, sem seguir redirecionamento, com tempo e tamanho máximos.
 * O caminho é montado pelo adaptador com um identificador já validado, e o host nunca vem do usuário. Por isso não
 * há como apontar a coleta para a rede interna (SSRF).
 */
@Component
public class BoundedHttpFetcher {

    static final int MAX_BYTES = 8 * 1024 * 1024;
    static final Set<String> ALLOWED_HOSTS = Set.of(
            "boards-api.greenhouse.io", "api.lever.co", "api.smartrecruiters.com", "api.ashbyhq.com");
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public byte[] get(URI uri) {
        if (!"https".equals(uri.getScheme()) || !ALLOWED_HOSTS.contains(uri.getHost()) || uri.getRawUserInfo() != null) {
            throw new CollectionException("Destino de coleta não permitido");
        }
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("User-Agent", "JobFly/0.2 (coleta de vagas publicas)")
                .GET()
                .build();
        try {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                int status = response.statusCode();
                if (status == 404) {
                    throw new CollectionException("Board não encontrado no ATS. Confira o identificador.");
                }
                if (status == 429) {
                    throw new CollectionException("O ATS limitou as requisições. Nova tentativa mais tarde.");
                }
                if (status < 200 || status >= 300) {
                    throw new CollectionException("O ATS respondeu com status " + status);
                }
                byte[] bytes = body.readNBytes(MAX_BYTES + 1);
                if (bytes.length > MAX_BYTES) {
                    throw new CollectionException("Resposta do ATS acima de 8 MiB");
                }
                return bytes;
            }
        } catch (IOException ex) {
            throw new CollectionException("Não foi possível falar com o ATS", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new CollectionException("Coleta interrompida", ex);
        }
    }
}
