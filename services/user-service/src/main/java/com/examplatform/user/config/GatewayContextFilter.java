package com.examplatform.user.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * Reads X-User-Id / X-User-Role / X-User-Email injected by the API Gateway
 * and exposes them as request attributes so controllers can use @RequestAttribute.
 */
@Component
@Order(1)
public class GatewayContextFilter implements Filter {

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest http = (HttpServletRequest) req;
        String userId = http.getHeader("X-User-Id");
        String role   = http.getHeader("X-User-Role");
        String email  = http.getHeader("X-User-Email");

        if (userId != null) {
            try { http.setAttribute("userId", UUID.fromString(userId)); } catch (Exception ignored) {}
        }
        if (role  != null) http.setAttribute("userRole",  role);
        if (email != null) http.setAttribute("userEmail", email);

        chain.doFilter(req, res);
    }
}
