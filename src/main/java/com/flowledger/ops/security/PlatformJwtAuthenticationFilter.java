package com.flowledger.ops.security;

import com.flowledger.iam.config.IamProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates platform JWTs on /api/v1/ops/**. Skips auth paths. Disabled when IAM SSO is enabled
 * (see PlatformIamJwtAuthenticationFilter).
 */
@Slf4j
@Component
@Order(50)
public class PlatformJwtAuthenticationFilter extends OncePerRequestFilter {
    private final PlatformJwtService jwt;
    private final PlatformUserDetailsService users;
    private final IamProperties iamProperties;

    public PlatformJwtAuthenticationFilter(
            PlatformJwtService jwt, PlatformUserDetailsService users, IamProperties iamProperties) {
        this.jwt = jwt;
        this.users = users;
        this.iamProperties = iamProperties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (iamProperties.isEnabled()) {
            return true;
        }
        String path = request.getRequestURI();
        return path == null || !path.startsWith("/api/v1/ops/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path != null && path.startsWith("/api/v1/ops/auth/")) {
            chain.doFilter(request, response);
            return;
        }
        String authorizationHeader = request.getHeader("Authorization");
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            writeUnauthorized(response, "Platform authentication required");
            return;
        }
        String token = authorizationHeader.substring(7).trim();
        if (!jwt.isValidPlatformAccess(token)) {
            writeUnauthorized(response, "Platform access token expired or invalid");
            return;
        }
        try {
            UUID userId = jwt.userId(token);
            PlatformPrincipal principal = users.load(userId);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);
            chain.doFilter(request, response);
        } catch (Exception ex) {
            log.debug("Platform JWT load failed: {}", ex.getMessage());
            writeUnauthorized(response, "Platform access token expired or invalid");
        }
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
