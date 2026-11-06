package dev.jobfly.common.security;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Janela deslizante de tentativas por chave (IP + e-mail) nos endpoints de autenticação.
 * Fica em memória, o que basta para uma instância. Com várias instâncias, a troca prevista é por Redis (roadmap).
 */
@Component
public class LoginRateLimiter {

    private static final int MAX_KEYS = 50_000;

    private final SecurityProperties.Login config;
    private final Clock clock;
    private final Map<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();

    public LoginRateLimiter(SecurityProperties properties, Clock clock) {
        this.config = properties.login();
        this.clock = clock;
    }

    /** Registra a tentativa e diz se ela está dentro do limite. */
    public boolean tryAcquire(String key) {
        Instant now = clock.instant();
        Instant windowStart = now.minus(config.window());
        if (attempts.size() > MAX_KEYS) {
            attempts.entrySet().removeIf(entry -> isExpired(entry.getValue(), windowStart));
        }
        Deque<Instant> window = attempts.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && window.peekFirst().isBefore(windowStart)) {
                window.pollFirst();
            }
            if (window.size() >= config.maxAttemptsPerWindow()) {
                return false;
            }
            window.addLast(now);
            return true;
        }
    }

    private static boolean isExpired(Deque<Instant> window, Instant windowStart) {
        synchronized (window) {
            return window.isEmpty() || window.peekLast().isBefore(windowStart);
        }
    }
}
