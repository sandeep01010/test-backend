package com.examplatform.payment.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final GatewayContextFilter gatewayContextFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Stateless — no session, CSRF not needed
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(AbstractHttpConfigurer::disable)
            // CORS handled by API gateway
            .cors(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                // Razorpay webhook is public — Razorpay servers call this directly
                .requestMatchers("/api/v1/payments/webhook").permitAll()
                // Actuator for health checks and metrics
                .requestMatchers("/actuator/**").permitAll()
                // Admin endpoints restricted to ADMIN and SUPER_ADMIN roles
                .requestMatchers("/api/v1/payments/admin/**")
                    .hasAnyRole("ADMIN", "SUPER_ADMIN")
                // All other payment endpoints require authentication
                .anyRequest().authenticated()
            )
            // Insert our gateway context filter before the standard auth filter
            .addFilterBefore(gatewayContextFilter, UsernamePasswordAuthenticationFilter.class)
            // Disable form login and HTTP basic — we rely on gateway headers
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable);

        return http.build();
    }
}
