package com.examplatform.testengine.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Reads trusted identity headers injected by the API Gateway's JwtAuthFilter:
 *   X-User-Id, X-User-Role, X-User-Email
 *
 * Converts X-User-Id into a request attribute "userId" (UUID) so controllers
 * can use @RequestAttribute("userId") without needing Spring Security.
 *
 * Authentication enforcement is handled upstream by the API Gateway.
 */
@Component
public class GatewayContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {

        String userId = request.getHeader("X-User-Id");
        String role   = request.getHeader("X-User-Role");

        if (userId != null && !userId.isBlank()) {
            try {
                request.setAttribute("userId", UUID.fromString(userId.trim()));
            } catch (IllegalArgumentException ignored) {
                // Malformed UUID from header — leave attribute unset
            }
        }

        // Store role as attribute too (useful for controller-level role checks)
        if (role != null && !role.isBlank()) {
            request.setAttribute("userRole", role.trim().toUpperCase());
        }

        chain.doFilter(request, response);
    }
}
