package com.examplatform.payment.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Filter that reads pre-validated headers set by the API Gateway.
 * The gateway validates the JWT and forwards user context as headers:
 *   X-User-Id    → userId attribute
 *   X-User-Role  → userRole attribute
 *   X-User-Email → userEmail attribute
 *
 * This service trusts these headers only when coming from the gateway (enforced by
 * network policy / service mesh in production). No JWT validation happens here.
 */
@Slf4j
@Component
public class GatewayContextFilter extends OncePerRequestFilter {

    public static final String HEADER_USER_ID    = "X-User-Id";
    public static final String HEADER_USER_ROLE  = "X-User-Role";
    public static final String HEADER_USER_EMAIL = "X-User-Email";

    public static final String ATTR_USER_ID    = "userId";
    public static final String ATTR_USER_ROLE  = "userRole";
    public static final String ATTR_USER_EMAIL = "userEmail";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String userId    = request.getHeader(HEADER_USER_ID);
        String userRole  = request.getHeader(HEADER_USER_ROLE);
        String userEmail = request.getHeader(HEADER_USER_EMAIL);

        if (userId != null && !userId.isBlank()) {
            request.setAttribute(ATTR_USER_ID, userId);
        }
        if (userRole != null && !userRole.isBlank()) {
            request.setAttribute(ATTR_USER_ROLE, userRole);
        }
        if (userEmail != null && !userEmail.isBlank()) {
            request.setAttribute(ATTR_USER_EMAIL, userEmail);
        }

        // Build Spring Security principal from gateway-provided headers
        if (userId != null && !userId.isBlank() && userRole != null && !userRole.isBlank()) {
            String roleWithPrefix = userRole.startsWith("ROLE_") ? userRole : "ROLE_" + userRole;
            List<SimpleGrantedAuthority> authorities =
                    Collections.singletonList(new SimpleGrantedAuthority(roleWithPrefix));

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, null, authorities);
            authentication.setDetails(userEmail);

            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.debug("Set security context for userId={} role={}", userId, roleWithPrefix);
        }

        filterChain.doFilter(request, response);
    }
}
