package com.examplatform.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Slf4j
@Component
public class JwtAuthFilter extends AbstractGatewayFilterFactory<JwtAuthFilter.Config> {

    private final PublicKey publicKey;
    private final ReactiveStringRedisTemplate redisTemplate;

    public JwtAuthFilter(
            @Value("${jwt.public-key-pem}") String publicKeyPem,
            ReactiveStringRedisTemplate redisTemplate) throws Exception {
        super(Config.class);
        PublicKey resolvedKey = null;
        if (!"GENERATE_ME".equals(publicKeyPem) && publicKeyPem != null && !publicKeyPem.isBlank()) {
            try {
                resolvedKey = loadPublicKey(publicKeyPem);
            } catch (Exception e) {
                log.warn("JWT_PUBLIC_KEY_PEM is set but could not be decoded ({}). " +
                         "Falling back to ephemeral dev key.", e.getMessage());
            }
        }
        if (resolvedKey == null) {
            log.warn("Generating ephemeral dev key pair. Tokens will NOT survive restarts. " +
                     "Set a valid JWT_PUBLIC_KEY_PEM for production.");
            java.security.KeyPairGenerator kpg = java.security.KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            resolvedKey = kpg.generateKeyPair().getPublic();
        }
        this.publicKey = resolvedKey;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return unauthorizedResponse(exchange.getResponse(), "Missing Authorization header");
            }

            String token = authHeader.substring(7);

            try {
                Claims claims = Jwts.parser()
                        .verifyWith(publicKey)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();

                String jti = claims.getId();

                // Async blacklist check in Redis — fail open on Redis hiccups (timeout/connection
                // error) rather than rejecting every authenticated request when Redis blips.
                return redisTemplate.hasKey("auth:blacklist:" + jti)
                        .timeout(java.time.Duration.ofSeconds(2))
                        .onErrorReturn(false)
                        .flatMap(blacklisted -> {
                            if (Boolean.TRUE.equals(blacklisted)) {
                                return unauthorizedResponse(exchange.getResponse(), "Token revoked");
                            }

                            // Inject user context as headers for downstream services
                            ServerHttpRequest mutated = request.mutate()
                                    .header("X-User-Id", claims.getSubject())
                                    .header("X-User-Role", (String) claims.get("role"))
                                    .header("X-User-Email", (String) claims.get("email"))
                                    .build();

                            return chain.filter(exchange.mutate().request(mutated).build());
                        });

            } catch (JwtException e) {
                log.warn("JWT validation failed: {}", e.getMessage());
                return unauthorizedResponse(exchange.getResponse(), "Invalid or expired token");
            }
        };
    }

    private Mono<Void> unauthorizedResponse(ServerHttpResponse response, String message) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = String.format("{\"error\":\"Unauthorized\",\"message\":\"%s\"}", message);
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes());
        return response.writeWith(Mono.just(buffer));
    }

    private PublicKey loadPublicKey(String pem) throws Exception {
        String stripped = pem.trim();
        byte[] decoded;
        if (stripped.startsWith("-----")) {
            // PEM format — strip headers and decode standard base64
            stripped = stripped
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            decoded = Base64.getDecoder().decode(stripped);
        } else {
            // Base64url-encoded DER format (URL-safe, no headers, no special chars)
            decoded = Base64.getUrlDecoder().decode(stripped);
        }
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(decoded));
    }

    public static class Config {}
}
