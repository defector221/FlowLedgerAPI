package com.flowledger.common.security;

import com.flowledger.auth.service.CustomUserDetailsService;
import com.flowledger.common.tenant.TenantContext;
import com.flowledger.iam.config.IamProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwt;
    private final CustomUserDetailsService users;
    private final IamProperties iamProperties;

    public JwtAuthenticationFilter(JwtService jwt, CustomUserDetailsService users, IamProperties iamProperties) {
        this.jwt = jwt;
        this.users = users;
        this.iamProperties = iamProperties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Legacy HS256 plane is retained only when IAM SSO is disabled.
        if (iamProperties.isEnabled()) {
            return true;
        }
        String path = request.getRequestURI();
        if (path != null && path.startsWith("/api/v1/ops/")) {
            return true;
        }
        if (path != null && isCommerceCustomerPath(path)) {
            return true;
        }
        return false;
    }

    private static boolean isCommerceCustomerPath(String path) {
        if (path.startsWith("/api/v1/commerce/carts")
                || path.startsWith("/api/v1/commerce/checkout")
                || path.startsWith("/api/v1/commerce/orders")
                || path.startsWith("/api/v1/commerce/scan")) {
            return true;
        }
        if (path.startsWith("/api/v1/commerce/auth/")) {
            return !path.equals("/api/v1/commerce/auth/refresh");
        }
        return path.startsWith("/api/v1/commerce/customers/me")
                || path.startsWith("/api/v1/commerce/customers/address");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            String authorizationHeader = request.getHeader("Authorization");
            if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
                String token = authorizationHeader.substring(7).trim();
                if (token.isEmpty()) {
                    writeUnauthorized(response, "Missing access token");
                    return;
                }
                if (!jwt.isValid(token, "access")) {
                    if (isPublicAuthPath(request)) {
                        chain.doFilter(request, response);
                        return;
                    }
                    writeUnauthorized(response, "Access token expired or invalid");
                    return;
                }
                try {
                    UUID userId = jwt.userId(token);
                    UUID organizationId = jwt.organizationId(token);
                    UUID branchId = jwt.branchId(token);
                    UUID storeId = jwt.storeId(token);
                    UUID warehouseId = jwt.warehouseId(token);
                    UserPrincipal principal = users.load(userId, organizationId, branchId, storeId, warehouseId);
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    TenantContext.set(principal.getOrgId(), principal.getId());
                    TenantContext.setLocation(branchId, storeId, warehouseId);
                } catch (Exception ex) {
                    log.debug("JWT principal load failed: {}", ex.getMessage());
                    writeUnauthorized(response, "Access token expired or invalid");
                    return;
                }
            }
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private static boolean isPublicAuthPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path != null
                && (path.startsWith("/api/v1/auth/")
                        || path.contains("/swagger-ui")
                        || path.contains("/api-docs")
                        || path.endsWith("/actuator/health"));
    }

    private static void writeUnauthorized(HttpServletResponse response, String detail) throws IOException {
        if (response.isCommitted()) return;
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        String escaped = detail.replace("\\", "\\\\").replace("\"", "\\\"");
        response.getWriter()
                .write(
                        "{\"type\":\"https://flowledger.com/problems/401\",\"title\":\"Unauthorized\",\"status\":401,\"detail\":\""
                                + escaped
                                + "\"}");
    }
}
