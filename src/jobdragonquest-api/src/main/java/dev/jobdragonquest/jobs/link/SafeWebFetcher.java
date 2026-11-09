package dev.jobdragonquest.jobs.link;

import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

import org.springframework.stereotype.Component;

import dev.jobdragonquest.common.error.DomainException;

/**
 * Busca uma página pública a partir de um link que a pessoa colou, com proteção contra SSRF.
 *
 * <ul>
 *   <li>Só http e https, nas portas padrão (80 e 443), sem usuário e senha no link.</li>
 *   <li>Todos os endereços do host são resolvidos antes; qualquer um interno (loopback, rede local, link-local,
 *       CGNAT, multicast, IPv6 privado, "qualquer endereço") faz recusar.</li>
 *   <li>Redirecionamento não é seguido automaticamente: cada salto passa pela mesma checagem, até 3.</li>
 *   <li>Até 2 MiB e 15 segundos por página.</li>
 * </ul>
 * Risco que fica: entre a checagem e a conexão o DNS pode mudar (DNS rebinding). A API roda no computador da própria
 * pessoa e devolve o conteúdo só para ela, o que limita o impacto.
 */
@Component
public class SafeWebFetcher {

    static final int MAX_BYTES = 2 * 1024 * 1024;
    static final int MAX_REDIRECTS = 3;
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public String fetchHtml(URI start) {
        return new String(fetch(start, MAX_BYTES, "text/html,application/xhtml+xml"), StandardCharsets.UTF_8);
    }

    /** Baixa um documento público (HTML ou PDF) com as mesmas proteções e o limite informado. */
    public byte[] fetchBytes(URI start, int maxBytes) {
        return fetch(start, maxBytes, "text/html,application/pdf,text/plain");
    }

    private byte[] fetch(URI start, int maxBytes, String accept) {
        URI current = start;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            checkDestination(current);
            HttpRequest request = HttpRequest.newBuilder(current)
                    .timeout(TIMEOUT)
                    .header("Accept", accept)
                    .header("User-Agent", "JobDragonQuest (leitura de vaga a pedido da pessoa)")
                    .GET()
                    .build();
            try {
                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                try (InputStream body = response.body()) {
                    int status = response.statusCode();
                    if (status >= 300 && status < 400) {
                        String location = response.headers().firstValue("Location")
                                .orElseThrow(() -> DomainException.badRequest("link_unreadable", "A página redirecionou sem destino."));
                        current = current.resolve(location);
                        continue;
                    }
                    if (status < 200 || status >= 300) {
                        throw DomainException.badRequest("link_unreadable", "A página respondeu com status " + status + ".");
                    }
                    byte[] bytes = body.readNBytes(maxBytes + 1);
                    if (bytes.length > maxBytes) {
                        throw DomainException.badRequest("link_too_large", "O conteúdo é grande demais.");
                    }
                    return bytes;
                }
            } catch (IOException ex) {
                throw DomainException.badRequest("link_unreadable", "Não foi possível abrir a página.");
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw DomainException.badRequest("link_unreadable", "Leitura interrompida.");
            }
        }
        throw DomainException.badRequest("link_unreadable", "Redirecionamentos demais.");
    }

    public static void checkDestination(URI uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        boolean defaultPort = uri.getPort() == -1 || (uri.getPort() == 443 && scheme.equals("https"))
                || (uri.getPort() == 80 && scheme.equals("http"));
        if (!(scheme.equals("https") || scheme.equals("http")) || uri.getHost() == null || uri.getRawUserInfo() != null || !defaultPort) {
            throw blocked();
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(uri.getHost());
        } catch (UnknownHostException ex) {
            throw DomainException.badRequest("link_unreadable", "Endereço do link não encontrado.");
        }
        for (InetAddress address : addresses) {
            if (isInternal(address)) {
                throw blocked();
            }
        }
    }

    /** Endereço que não é da internet pública. */
    static boolean isInternal(InetAddress address) {
        if (address.isLoopbackAddress() || address.isAnyLocalAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return true;
        }
        byte[] b = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = b[0] & 0xff;
            int second = b[1] & 0xff;
            return first == 0 || first >= 224 // 0.0.0.0/8, multicast e reservados
                    || (first == 100 && second >= 64 && second <= 127) // CGNAT 100.64.0.0/10
                    || (first == 192 && second == 0 && (b[2] & 0xff) == 0) // 192.0.0.0/24
                    || (first == 198 && (second == 18 || second == 19)); // testes de desempenho
        }
        if (address instanceof Inet6Address) {
            int first = b[0] & 0xff;
            if ((first & 0xfe) == 0xfc) { // fc00::/7, IPv6 privado
                return true;
            }
            // IPv4 embutido em IPv6 (::ffff:a.b.c.d): checa o IPv4.
            boolean mapped = true;
            for (int i = 0; i < 10; i++) {
                mapped &= b[i] == 0;
            }
            if (mapped && (b[10] & 0xff) == 0xff && (b[11] & 0xff) == 0xff) {
                try {
                    return isInternal(InetAddress.getByAddress(new byte[] {b[12], b[13], b[14], b[15]}));
                } catch (UnknownHostException ex) {
                    return true;
                }
            }
        }
        return false;
    }

    private static DomainException blocked() {
        return DomainException.badRequest("link_blocked", "Esse link não pode ser aberto: use um endereço público http ou https.");
    }
}
