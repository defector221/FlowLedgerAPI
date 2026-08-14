package com.flowledger.iam.security;

import com.flowledger.common.security.UserPrincipal;
import com.flowledger.common.tenant.TenantContext;
import com.flowledger.iam.client.IamClient;
import com.flowledger.iam.client.IamDtos;
import com.flowledger.iam.config.IamProperties;
import com.flowledger.iam.service.IamAuthService;
import com.flowledger.iam.service.IamShellProvisioningService;
import com.nimbusds.jwt.JWTClaimsSet;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collection;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * When IAM is enabled, authenticates via HttpOnly BFF session cookie (preferred) or rejects HS256
 * Bearer tokens. Keycloak access tokens stay server-side.
 */
@Slf4j
@Component
@Order(40)
public class IamJwtAuthenticationFilter extends OncePerRequestFilter {
    private final IamProperties properties;
    private final IamOidcTokenValidator tokenValidator;
    private final IamPermissionCache permissionCache;
    private final IamIdentityCache identityCache;
    private final IamClient iamClient;
    private final IamShellProvisioningService shells;
    private final IamAuthService iamAuth;

    public IamJwtAuthenticationFilter(
            IamProperties properties,
            IamOidcTokenValidator tokenValidator,
            IamPermissionCache permissionCache,
            IamIdentityCache identityCache,
            IamClient iamClient,
            IamShellProvisioningService shells,
            IamAuthService iamAuth) {
        this.properties = properties;
        this.tokenValidator = tokenValidator;
        this.permissionCache = permissionCache;
        this.identityCache = identityCache;
        this.iamClient = iamClient;
        this.shells = shells;
        this.iamAuth = iamAuth;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.isEnabled()) return true;
        String path = request.getRequestURI();
        if (path == null) return true;
        if (path.startsWith("/api/v1/ops/")) return true;
        if (path.startsWith("/api/v1/commerce/")) return true;
        if (path.startsWith("/api/v1/auth/")) return true;
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            String authorizationHeader = request.getHeader("Authorization");
            if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
                String token = authorizationHeader.substring(7).trim();
                if (looksLikeLegacyHs256(token)) {
                    writeUnauthorized(response, "Legacy HS256 tokens are disabled while IAM SSO is enabled");
                    return;
                }
                // Accidental Bearer of Keycloak token from clients is rejected — use session cookie.
                writeUnauthorized(response, "Use IAM session cookie; do not send Keycloak access tokens from the browser");
                return;
            }

            IamServerSessionStore.Session session = iamAuth.ensureFreshAccess(request, false);
            if (session == null || !StringUtils.hasText(session.accessToken())) {
                chain.doFilter(request, response);
                return;
            }
            try {
                JWTClaimsSet claims = tokenValidator.validateAccessToken(session.accessToken());
                IamIdentityCache.Identity identity = resolveIdentity(claims, session);
                UUID iamMembershipId = session.iamMembershipId();
                if (iamMembershipId == null) {
                    writeUnauthorized(response, "IAM session missing active membership");
                    return;
                }
                TenantContext.bindImmutable(
                        identity.localOrgId(),
                        identity.localUserId(),
                        identity.iamUserId(),
                        identity.iamOrgId(),
                        iamMembershipId);
                Collection<String> authorities = resolveAuthorities(
                        session.accessToken(), identity.iamUserId(), identity.iamOrgId(), iamMembershipId);
                if (authorities.isEmpty()) {
                    writeUnauthorized(response, "Authorization context missing or inactive for active membership");
                    return;
                }
                UserPrincipal principal = UserPrincipal.of(
                        identity.localUserId(), identity.localOrgId(), identity.email(), "N/A", authorities, true);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception ex) {
                log.debug("IAM session authentication failed: {}", ex.getMessage());
                writeUnauthorized(response, "IAM session expired or invalid");
                return;
            }
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private Collection<String> resolveAuthorities(
            String accessToken, UUID iamUserId, UUID iamOrgId, UUID iamMembershipId) {
        IamPermissionCache.AuthorizationContext cached =
                permissionCache.get(iamUserId, iamOrgId, iamMembershipId);
        if (cached == null) {
            cached = permissionCache.getOrFetchEffective(accessToken, iamUserId, iamOrgId, iamMembershipId);
        }
        return permissionCache.authoritiesMatching(cached);
    }

    private IamIdentityCache.Identity resolveIdentity(JWTClaimsSet claims, IamServerSessionStore.Session session) {
        String subject = claims.getSubject();
        IamIdentityCache.Identity cached = identityCache.get(subject);
        if (cached != null && !cached.platform()) {
            return cached;
        }
        if (session.localUserId() != null && session.localOrgId() != null) {
            IamIdentityCache.Identity fromSession = new IamIdentityCache.Identity(
                    session.localUserId(),
                    session.localOrgId(),
                    session.iamUserId(),
                    session.iamOrganizationId(),
                    session.email(),
                    false);
            identityCache.put(subject, fromSession);
            return fromSession;
        }
        IamDtos.MeResult me = iamClient.me(session.accessToken());
        if (me == null || me.userId() == null || me.organizationId() == null) {
            throw new IllegalArgumentException("Unable to resolve IAM identity");
        }
        IamShellProvisioningService.ShellResult shell = shells.provisionTenant(me);
        IamIdentityCache.Identity identity = new IamIdentityCache.Identity(
                shell.user().getId(),
                shell.organization().getId(),
                me.userId(),
                me.organizationId(),
                shell.user().getEmail(),
                false);
        identityCache.put(subject, identity);
        if (me.membershipId() != null) {
            IamPermissionCache.AuthorizationContext fromMe = permissionCache.getOrFetchEffective(
                    session.accessToken(), me.userId(), me.organizationId(), me.membershipId());
            if (fromMe != null) {
                permissionCache.put(fromMe);
            }
        }
        return identity;
    }

    private static boolean looksLikeLegacyHs256(String token) {
        try {
            String headerJson = new String(
                    java.util.Base64.getUrlDecoder().decode(pad(token.split("\\.")[0])),
                    java.nio.charset.StandardCharsets.UTF_8);
            return headerJson.contains("\"alg\":\"HS256\"");
        } catch (Exception e) {
            return false;
        }
    }

    private static String pad(String b64) {
        int mod = b64.length() % 4;
        if (mod == 0) return b64;
        return b64 + "====".substring(mod);
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
