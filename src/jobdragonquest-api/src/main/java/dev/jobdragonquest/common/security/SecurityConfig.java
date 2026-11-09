package dev.jobdragonquest.common.security;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import dev.jobdragonquest.common.error.ProblemResponses;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import dev.jobdragonquest.identity.PersonalTokenService;
import jakarta.servlet.http.HttpServletRequest;

/**
 * API sem estado: cada requisição traz um JWT no cabeçalho Authorization.
 * O CSRF fica desligado porque o token de acesso não vai em cookie. O único cookie (refresh token) é
 * HttpOnly, SameSite=Strict e restrito a /api/v1/auth, e esses endpoints também conferem o cabeçalho Origin.
 */
@Configuration
public class SecurityConfig {

    static final String ROLES_CLAIM = "roles";

    @Bean
    SecurityFilterChain api(HttpSecurity http, SecurityProperties properties, JwtDecoder jwtDecoder,
            PersonalTokenService personalTokens) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsSource(properties)))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .frameOptions(frame -> frame.deny()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/register", "/api/v1/auth/login",
                                "/api/v1/auth/refresh", "/api/v1/auth/logout", "/api/v1/auth/recover").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(ProblemResponses.unauthorized())
                        .accessDeniedHandler(ProblemResponses.forbidden()))
                .oauth2ResourceServer(oauth -> oauth
                        .authenticationManagerResolver(managerResolver(jwtDecoder, personalTokens))
                        .authenticationEntryPoint(ProblemResponses.unauthorized())
                        .accessDeniedHandler(ProblemResponses.forbidden()));
        return http.build();
    }

    /**
     * Dois tipos de Bearer: o JWT do painel e o token pessoal ("jdq_…") das integrações. O prefixo só escolhe o
     * validador; cada um confere o token por completo e qualquer falha vira 401 sem detalhe.
     */
    static AuthenticationManagerResolver<HttpServletRequest> managerResolver(JwtDecoder jwtDecoder,
            PersonalTokenService personalTokens) {
        JwtAuthenticationProvider jwtProvider = new JwtAuthenticationProvider(jwtDecoder);
        jwtProvider.setJwtAuthenticationConverter(authenticationConverter());
        AuthenticationManager jwtManager = new ProviderManager(jwtProvider);
        AuthenticationManager tokenManager = authentication -> {
            String value = authentication instanceof BearerTokenAuthenticationToken bearer ? bearer.getToken() : null;
            return personalTokens.authenticate(value)
                    .orElseThrow(() -> new InvalidBearerTokenException("Token inválido"));
        };
        return request -> {
            String header = request.getHeader(HttpHeaders.AUTHORIZATION);
            boolean personal = header != null && header.regionMatches(true, 0, "Bearer " + PersonalTokenService.PREFIX, 0,
                    7 + PersonalTokenService.PREFIX.length());
            return personal ? tokenManager : jwtManager;
        };
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        // Parâmetros recomendados pelo Spring Security 5.8+ (memória 16 MiB, 2 iterações).
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    @Bean
    JwtEncoder jwtEncoder(SecurityProperties properties) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey(properties)));
    }

    @Bean
    JwtDecoder jwtDecoder(SecurityProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey(properties))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        OAuth2TokenValidator<Jwt> audience = jwt -> jwt.getAudience() != null && jwt.getAudience().contains(properties.audience())
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Audiência inválida", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.issuer()), audience));
        return decoder;
    }

    private static SecretKey secretKey(SecurityProperties properties) {
        return new SecretKeySpec(properties.jwtSecretBytes(), "HmacSHA256");
    }

    private static Converter<Jwt, AbstractAuthenticationToken> authenticationConverter() {
        return jwt -> {
            Collection<String> roles = jwt.getClaimAsStringList(ROLES_CLAIM);
            List<GrantedAuthority> authorities = roles == null ? List.of() : roles.stream()
                    .<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .toList();
            return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
        };
    }

    private static CorsConfigurationSource corsSource(SecurityProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key", "Accept-Language"));
        config.setExposedHeaders(List.of("Location", "X-Duplicate-Of"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        CorsConfiguration extension = extensionCors();
        CorsConfiguration denied = new CorsConfiguration();
        return request -> {
            String origin = request.getHeader("Origin");
            if (origin != null && (origin.startsWith("chrome-extension://") || origin.startsWith("moz-extension://"))) {
                String path = request.getRequestURI().substring(request.getContextPath().length());
                return EXTENSION_PATHS.contains(path) ? extension : denied;
            }
            return source.getCorsConfiguration(request);
        };
    }

    /** Rotas que a extensão do navegador usa: ler a vaga da página e cadastrar. */
    static final Set<String> EXTENSION_PATHS = Set.of("/api/v1/jobs/from-link", "/api/v1/jobs");

    /**
     * CORS para a extensão gratuita do JobDragonQuest (issue #116). Sem cookies ({@code allowCredentials = false}): a
     * extensão manda o token pessoal no cabeçalho, e nenhuma outra extensão consegue usar a sessão do navegador.
     * Só POST nas duas rotas da extensão; qualquer outra rota recusa origem de extensão.
     */
    private static CorsConfiguration extensionCors() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("chrome-extension://*", "moz-extension://*"));
        config.setAllowedMethods(List.of("POST"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(false);
        config.setMaxAge(600L);
        return config;
    }
}
