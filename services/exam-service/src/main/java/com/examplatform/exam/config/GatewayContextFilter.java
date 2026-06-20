package com.examplatform.exam.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * The API Gateway verifies the JWT and forwards identity as trusted headers:
 *   X-User-Id, X-User-Role, X-User-Email
 *
 * This filter turns those headers into:
 *   - a request attribute "userId" (UUID)  -> read via @RequestAttribute("userId")
 *   - a Spring Security Authentication with ROLE_<role> -> enforced by @PreAuthorize
 *
 * Without this, controller role checks and userId injection would not work.
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
        String email  = request.getHeader("X-User-Email");

        if (userId != null && !userId.isBlank()) {
            try {
                request.setAttribute("userId", UUID.fromString(userId));
            } catch (IllegalArgumentException ignored) { /* malformed — leave unset */ }
        }

        if (role != null && !role.isBlank()) {
            request.setAttribute("userRole", role.trim().toUpperCase());
            var authority = new SimpleGrantedAuthority("ROLE_" + role.trim().toUpperCase());
            var auth = new UsernamePasswordAuthenticationToken(
                    email != null ? email : userId, null, List.of(authority));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        chain.doFilter(request, response);
    }
}
