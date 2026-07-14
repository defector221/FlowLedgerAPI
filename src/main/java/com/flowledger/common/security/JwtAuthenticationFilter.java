package com.flowledger.common.security;

import com.flowledger.auth.service.CustomUserDetailsService;
import com.flowledger.common.tenant.TenantContext;
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

    public JwtAuthenticationFilter(JwtService jwt, CustomUserDetailsService users) {
        this.jwt = jwt;
        this.users = users;
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
                    // Expired / malformed / wrong type — must be 401 so the UI can refresh or log out
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
                    UserPrincipal principal = users.load(userId, organizationId);
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    TenantContext.set(principal.getOrgId(), principal.getId());
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
