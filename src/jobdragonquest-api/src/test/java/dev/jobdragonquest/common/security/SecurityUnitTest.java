package dev.jobdragonquest.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.Test;

class SecurityUnitTest {

    private static final String SECRET_32 = Base64.getEncoder().encodeToString(new byte[32]);
    private static final SecurityProperties.Login LOGIN = new SecurityProperties.Login(3, Duration.ofMinutes(5), 5,
            Duration.ofMinutes(15));

    private static SecurityProperties properties(String secret) {
        return new SecurityProperties(secret, "jobdragonquest", "jobdragonquest-api", Duration.ofMinutes(15), Duration.ofDays(14), true,
                List.of("http://localhost:5173"), LOGIN);
    }

    @Test
    void refusesToStartWithoutStrongSecret() {
        assertThatThrownBy(() -> properties("")).hasMessageContaining("JOBDRAGONQUEST_JWT_SECRET");
        assertThatThrownBy(() -> properties(null)).hasMessageContaining("JOBDRAGONQUEST_JWT_SECRET");
        assertThatThrownBy(() -> properties("não é base64!")).hasMessageContaining("Base64");
        assertThatThrownBy(() -> properties(Base64.getEncoder().encodeToString(new byte[16]))).hasMessageContaining("32 bytes");
        assertThat(properties(SECRET_32).jwtSecretBytes()).hasSize(32);
    }

    @Test
    void rateLimiterBlocksAfterLimitAndReleasesAfterWindow() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-05T12:00:00Z"));
        LoginRateLimiter limiter = new LoginRateLimiter(properties(SECRET_32), clock);

        assertThat(limiter.tryAcquire("login|1.2.3.4|a@b.com")).isTrue();
        assertThat(limiter.tryAcquire("login|1.2.3.4|a@b.com")).isTrue();
        assertThat(limiter.tryAcquire("login|1.2.3.4|a@b.com")).isTrue();
        assertThat(limiter.tryAcquire("login|1.2.3.4|a@b.com")).isFalse();
        assertThat(limiter.tryAcquire("login|5.6.7.8|a@b.com")).as("outra chave não é afetada").isTrue();

        clock.advance(Duration.ofMinutes(6));
        assertThat(limiter.tryAcquire("login|1.2.3.4|a@b.com")).isTrue();
    }

    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
