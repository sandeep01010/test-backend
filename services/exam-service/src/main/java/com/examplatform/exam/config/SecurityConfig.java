package com.examplatform.exam.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import lombok.RequiredArgsConstructor;

/**
 * Exam Service runs BEHIND the API Gateway which handles JWT auth.
 * GatewayContextFilter promotes the trusted X-User-* headers into a Spring
 * Security Authentication so @PreAuthorize role checks are enforced here.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final GatewayContextFilter gatewayContextFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**").permitAll()
                // HTTP-layer is open; fine-grained access is enforced by @PreAuthorize
                // using the role promoted from the gateway headers.
                .requestMatchers("/api/**", "/exams/**", "/questions/**").permitAll()
                .anyRequest().permitAll()
            )
            .addFilterBefore(gatewayContextFilter, UsernamePasswordAuthenticationFilter.class)
            .headers(h -> h
                .frameOptions(f -> f.deny())
                .contentSecurityPolicy(c -> c.policyDirectives("default-src 'self'"))
            );
        return http.build();
    }
}
