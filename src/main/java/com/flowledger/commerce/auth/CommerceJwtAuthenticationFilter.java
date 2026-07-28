package com.flowledger.commerce.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(40)
public class CommerceJwtAuthenticationFilter extends OncePerRequestFilter {
    private final CommerceJwtService jwt;
    private final CommerceCustomerDetailsService customers;

    public CommerceJwtAuthenticationFilter(CommerceJwtService jwt, CommerceCustomerDetailsService customers) {
        this.jwt = jwt;
        this.customers = customers;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null || !path.startsWith("/api/v1/commerce/")) {
            return true;
        }
        if (isPublicCommercePath(path, request.getMethod())) {
            return true;
        }
        return !isCustomerCommercePath(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String authorizationHeader = request.getHeader("Authorization");
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            writeUnauthorized(response, "Commerce authentication required");
            return;
        }
        String token = authorizationHeader.substring(7).trim();
        if (!jwt.isValidAccess(token)) {
            writeUnauthorized(response, "Commerce token expired or invalid");
            return;
        }
        try {
            UUID customerId = jwt.customerId(token);
            CommercePrincipal principal = customers.load(customerId);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);
            chain.doFilter(request, response);
        } catch (Exception ex) {
            writeUnauthorized(response, "Commerce authentication failed");
        }
    }

    static boolean isPublicCommercePath(String path, String method) {
        if (path.startsWith("/api/v1/commerce/auth/request-otp")
                || path.startsWith("/api/v1/commerce/auth/verify-otp")
                || path.equals("/api/v1/commerce/auth/refresh")) {
            return true;
        }
        if (HttpMethod.POST.matches(method) && "/api/v1/commerce/customers".equals(path)) {
            return true;
        }
        if (path.startsWith("/api/v1/commerce/marketplace/")) {
            return true;
        }
        return path.startsWith("/api/v1/commerce/payments/webhooks/");
    }

    static boolean isCustomerCommercePath(String path) {
        if (path.startsWith("/api/v1/commerce/carts")) {
            return true;
        }
        if (path.startsWith("/api/v1/commerce/checkout")) {
            return true;
        }
        if (path.startsWith("/api/v1/commerce/orders")) {
            return true;
        }
        if (path.equals("/api/v1/commerce/customers/me")) {
            return true;
        }
        return path.startsWith("/api/v1/commerce/customers/address");
    }

    private static void writeUnauthorized(HttpServletResponse response, String detail) throws IOException {
        if (response.isCommitted()) return;
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter()
                .write("{\"type\":\"https://flowledger.com/problems/401\",\"title\":\"Unauthorized\",\"status\":401,\"detail\":\""
                        + detail.replace("\"", "\\\"")
                        + "\"}");
    }
}
