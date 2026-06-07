package com.examplatform.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimiterConfig {

    /**
     * Rate limit by IP address — for unauthenticated routes.
     */
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.justOrEmpty(
                exchange.getRequest().getRemoteAddress())
                .map(addr -> "ip:" + addr.getAddress().getHostAddress())
                .defaultIfEmpty("ip:unknown");
    }

    /**
     * Rate limit by user ID — for authenticated routes.
     * Falls back to IP if user header is missing.
     */
    @Bean
    @Primary
    public KeyResolver userKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
            if (userId != null) {
                return Mono.just("user:" + userId);
            }
            return Mono.justOrEmpty(exchange.getRequest().getRemoteAddress())
                    .map(addr -> "ip:" + addr.getAddress().getHostAddress())
                    .defaultIfEmpty("ip:unknown");
        };
    }
}
