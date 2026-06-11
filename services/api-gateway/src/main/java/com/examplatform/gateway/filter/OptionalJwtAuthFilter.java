package com.examplatform.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Optional authentication for PUBLIC-BROWSE routes (e.g. exam catalogue).
 *
 * Unlike {@link JwtAuthFilter}, this NEVER rejects a request:
 *   - valid token   -> inject X-User-Id / X-User-Role / X-User-Email headers
 *   - no token      -> pass through anonymously (no identity headers)
 *   - invalid token -> pass through anonymously
 *
 * This lets logged-out visitors browse categories and published exams, while
 * protected endpoints behind the same route stay guarded by @PreAuthorize in
 * the service (which denies when no identity headers are present).
 */
@Slf4j
@Component
public class OptionalJwtAuthFilter extends AbstractGatewayFilterFactory<OptionalJwtAuthFilter.Config> {

    private final PublicKey publicKey;
    private final ReactiveStringRedisTemplate redisTemplate;

    public OptionalJwtAuthFilter(
            @Value("${jwt.public-key-pem}") String publicKeyPem,
            ReactiveStringRedisTemplate redisTemplate) throws Exception {
        super(Config.class);
        PublicKey resolved = null;
        if (publicKeyPem != null && !publicKeyPem.isBlank() && !"GENERATE_ME".equals(publicKeyPem)) {
            try { resolved = loadPublicKey(publicKeyPem); }
            catch (Exception e) { log.warn("OptionalJwtAuthFilter: bad public key ({})", e.getMessage()); }
        }
        if (resolved == null) {
            var kpg = java.security.KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            resolved = kpg.generateKeyPair().getPublic();
        }
        this.publicKey = resolved;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

            // Anonymous — strip any client-supplied identity headers and continue.
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return chain.filter(exchange.mutate().request(stripIdentity(request)).build());
            }

            String token = authHeader.substring(7);
            try {
                Claims claims = Jwts.parser()
                        .verifyWith(publicKey)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();

                String jti = claims.getId();
                return redisTemplate.hasKey("auth:blacklist:" + jti)
                        .flatMap(blacklisted -> {
                            if (Boolean.TRUE.equals(blacklisted)) {
                                // Revoked token -> treat as anonymous, don't reject browsing.
                                return chain.filter(exchange.mutate().request(stripIdentity(request)).build());
                            }
                            ServerHttpRequest mutated = request.mutate()
                                    .header("X-User-Id", claims.getSubject())
                                    .header("X-User-Role", (String) claims.get("role"))
                                    .header("X-User-Email", (String) claims.get("email"))
                                    .build();
                            return chain.filter(exchange.mutate().request(mutated).build());
                        });
            } catch (JwtException e) {
                // Invalid/expired -> anonymous browse.
                return chain.filter(exchange.mutate().request(stripIdentity(request)).build());
            }
        };
    }

    /** Prevent header spoofing: clients must never set identity headers themselves. */
    private ServerHttpRequest stripIdentity(ServerHttpRequest request) {
        return request.mutate()
                .headers(h -> {
                    h.remove("X-User-Id");
                    h.remove("X-User-Role");
                    h.remove("X-User-Email");
                })
                .build();
    }

    private PublicKey loadPublicKey(String pem) throws Exception {
        String s = pem.trim();
        byte[] decoded;
        if (s.startsWith("-----")) {
            s = s.replace("-----BEGIN PUBLIC KEY-----", "")
                 .replace("-----END PUBLIC KEY-----", "")
                 .replaceAll("\\s", "");
            decoded = Base64.getDecoder().decode(s);
        } else {
            decoded = Base64.getUrlDecoder().decode(s);
        }
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(decoded));
    }

    public static class Config {}
}
