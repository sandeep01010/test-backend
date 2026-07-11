package com.examplatform.user.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/auth/register",
                    "/auth/login",
                    "/auth/otp/**",
                    "/auth/refresh",
                    "/auth/logout",      // reads Authorization header itself; no SecurityContext principal needed
                    "/auth/subscribe",              // auth enforced by gateway (JwtAuthFilter) + @RequestAttribute
                    "/auth/subscription-status",    // auth enforced by gateway (JwtAuthFilter) + @RequestAttribute
                    "/admin/users/**",   // auth enforced by gateway + role check in controller
                    "/actuator/health",
                    "/actuator/prometheus"
                ).permitAll()
                .anyRequest().authenticated()
            )
            .headers(h -> h
                .frameOptions(f -> f.deny())
                .xssProtection(x -> x.disable())  // handled by WAF
                .contentSecurityPolicy(c -> c.policyDirectives("default-src 'self'"))
            );

        return http.build();
    }
}
