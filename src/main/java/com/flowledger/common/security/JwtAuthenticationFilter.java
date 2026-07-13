package com.flowledger.common.security;

import com.flowledger.auth.service.CustomUserDetailsService;
import com.flowledger.common.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwt;
    private final CustomUserDetailsService users;

    public JwtAuthenticationFilter(
            JwtService jwt,
            CustomUserDetailsService users
    ) {
        this.jwt = jwt;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {

        log.debug(
                "Processing request method={}, uri={}",
                request.getMethod(),
                request.getRequestURI()
        );

        try {
            String authorizationHeader =
                    request.getHeader("Authorization");

            if (authorizationHeader == null) {
                log.debug("Authorization header is missing");
                chain.doFilter(request, response);
                return;
            }

            if (!authorizationHeader.startsWith("Bearer ")) {
                log.warn(
                        "Invalid Authorization header format uri={}",
                        request.getRequestURI()
                );

                chain.doFilter(request, response);
                return;
            }

            log.debug("Bearer token found");

            String token = authorizationHeader.substring(7);

            boolean valid = jwt.isValid(token, "access");

            log.debug("JWT validation result={}", valid);

            if (!valid) {
                log.warn(
                        "Invalid or expired access token uri={}",
                        request.getRequestURI()
                );

                chain.doFilter(request, response);
                return;
            }

            var userId = jwt.userId(token);

            log.debug("JWT userId={}", userId);

            UserPrincipal principal = users.load(userId);

            log.debug(
                    "User principal loaded id={}, orgId={}, authorities={}",
                    principal.getId(),
                    principal.getOrgId(),
                    principal.getAuthorities()
            );

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            principal,
                            null,
                            principal.getAuthorities()
                    );

            SecurityContextHolder
                    .getContext()
                    .setAuthentication(authentication);

            log.debug(
                    "SecurityContext authentication established authenticated={}, authorities={}",
                    authentication.isAuthenticated(),
                    authentication.getAuthorities()
            );

            TenantContext.set(
                    principal.getOrgId(),
                    principal.getId()
            );

            log.debug(
                    "TenantContext initialized orgId={}, userId={}",
                    principal.getOrgId(),
                    principal.getId()
            );

            chain.doFilter(request, response);

        } catch (Exception ex) {

            log.error(
                    "JWT authentication failed method={}, uri={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    ex
            );

            throw ex;

        } finally {

            log.debug(
                    "Clearing TenantContext method={}, uri={}",
                    request.getMethod(),
                    request.getRequestURI()
            );

            TenantContext.clear();
        }
    }
}