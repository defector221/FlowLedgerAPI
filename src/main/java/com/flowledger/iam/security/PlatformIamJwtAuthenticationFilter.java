package com.flowledger.iam.security;

import com.flowledger.iam.client.IamClient;
import com.flowledger.iam.client.IamDtos;
import com.flowledger.iam.config.IamProperties;
import com.flowledger.iam.service.IamAuthService;
import com.flowledger.iam.service.IamShellProvisioningService;
import com.flowledger.ops.entity.PlatformUser;
import com.flowledger.ops.repository.PlatformUserRepository;
import com.flowledger.ops.security.PlatformPrincipal;
import com.nimbusds.jwt.JWTClaimsSet;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Component
@Order(45)
public class PlatformIamJwtAuthenticationFilter extends OncePerRequestFilter {
    private final IamProperties properties;
    private final IamOidcTokenValidator tokenValidator;
    private final IamAuthorityMapper authorityMapper;
    private final IamPermissionCache permissionCache;
    private final IamIdentityCache identityCache;
    private final IamClient iamClient;
    private final PlatformUserRepository platformUsers;
    private final IamShellProvisioningService shells;
    private final IamAuthService iamAuth;

    public PlatformIamJwtAuthenticationFilter(
            IamProperties properties,
            IamOidcTokenValidator tokenValidator,
            IamAuthorityMapper authorityMapper,
            IamPermissionCache permissionCache,
            IamIdentityCache identityCache,
            IamClient iamClient,
            PlatformUserRepository platformUsers,
            IamShellProvisioningService shells,
            IamAuthService iamAuth) {
        this.properties = properties;
        this.tokenValidator = tokenValidator;
        this.authorityMapper = authorityMapper;
        this.permissionCache = permissionCache;
        this.identityCache = identityCache;
        this.iamClient = iamClient;
        this.platformUsers = platformUsers;
        this.shells = shells;
        this.iamAuth = iamAuth;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.isEnabled()) return true;
        String path = request.getRequestURI();
        return path == null || !path.startsWith("/api/v1/ops/") || path.startsWith("/api/v1/ops/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String authorizationHeader = request.getHeader("Authorization");
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            String token = authorizationHeader.substring(7).trim();
            if (looksLikeLegacyHs256(token)) {
                writeUnauthorized(response, "Legacy HS256 tokens are disabled while IAM SSO is enabled");
                return;
            }
            writeUnauthorized(response, "Use IAM session cookie; do not send Keycloak access tokens from the browser");
            return;
        }

        IamServerSessionStore.Session session = iamAuth.ensureFreshAccess(request, true);
        if (session == null || !StringUtils.hasText(session.accessToken())) {
            writeUnauthorized(response, "Platform authentication required");
            return;
        }
        try {
            JWTClaimsSet claims = tokenValidator.validateAccessToken(session.accessToken());
            IamIdentityCache.Identity identity = resolveIdentity(claims, session);
            PlatformUser user = platformUsers
                    .findById(identity.localUserId())
                    .orElseThrow(() -> new IllegalArgumentException("Platform shell missing"));
            UUID membershipId = session.iamMembershipId();
            List<String> claimRoles = List.of();
            List<String> claimPermissions = List.of();
            Collection<String> authorities;
            if (membershipId != null && identity.iamUserId() != null && identity.iamOrgId() != null) {
                IamPermissionCache.AuthorizationContext cached =
                        permissionCache.get(identity.iamUserId(), identity.iamOrgId(), membershipId);
                if (cached == null) {
                    cached = permissionCache.getOrFetchEffective(
                            session.accessToken(), identity.iamUserId(), identity.iamOrgId(), membershipId);
                }
                if (cached == null
                        || !isActive(cached.userStatus())
                        || !isActive(cached.organizationStatus())
                        || !isActive(cached.membershipStatus())) {
                    writeUnauthorized(response, "Platform authorization context missing or inactive");
                    return;
                }
                claimRoles = cached.roles();
                claimPermissions = cached.permissions();
                authorities = authorityMapper.toAuthorities(claimRoles, claimPermissions);
            } else {
                // Ops fallback without membership: still fail closed if effective authz unavailable.
                IamDtos.EffectiveAuthorization effective =
                        iamClient.effective(session.accessToken(), identity.iamOrgId());
                if (effective == null) {
                    writeUnauthorized(response, "Platform authorization context missing");
                    return;
                }
                claimRoles = effective.roles() == null ? List.of() : effective.roles();
                claimPermissions = effective.permissions() == null ? List.of() : effective.permissions();
                authorities = authorityMapper.toAuthorities(claimRoles, claimPermissions);
                if (effective.membershipId() != null) {
                    permissionCache.put(permissionCache.fromEffective(effective));
                }
            }
            if (authorities.isEmpty()) {
                writeUnauthorized(response, "Platform authorization empty");
                return;
            }
            Set<String> roles = new HashSet<>(claimRoles);
            Set<String> permissions = new HashSet<>(claimPermissions);
            if (permissions.isEmpty()) permissions.addAll(authorities);
            PlatformPrincipal principal = new PlatformPrincipal(
                    user.getId(),
                    user.getEmail(),
                    user.getPasswordHash(),
                    user.isActive(),
                    roles,
                    permissions,
                    authorities);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);
            chain.doFilter(request, response);
        } catch (Exception ex) {
            log.debug("Platform IAM session authentication failed: {}", ex.getMessage());
            writeUnauthorized(response, "Platform IAM session expired or invalid");
        }
    }

    private IamIdentityCache.Identity resolveIdentity(
            JWTClaimsSet claims, IamServerSessionStore.Session session) {
        String subject = claims.getSubject();
        IamIdentityCache.Identity cached = identityCache.get(subject);
        if (cached != null && cached.platform()) {
            return cached;
        }
        if (session.localUserId() != null) {
            IamIdentityCache.Identity fromSession = new IamIdentityCache.Identity(
                    session.localUserId(),
                    session.localOrgId(),
                    session.iamUserId(),
                    session.iamOrganizationId(),
                    session.email(),
                    true);
            identityCache.put(subject, fromSession);
            return fromSession;
        }
        IamDtos.MeResult me = iamClient.me(session.accessToken());
        if (me == null || me.userId() == null) {
            throw new IllegalArgumentException("Unable to resolve IAM platform identity");
        }
        PlatformUser user = shells.provisionPlatform(me);
        IamIdentityCache.Identity identity = new IamIdentityCache.Identity(
                user.getId(), me.organizationId(), me.userId(), me.organizationId(), user.getEmail(), true);
        identityCache.put(subject, identity);
        if (me.membershipId() != null) {
            IamPermissionCache.AuthorizationContext ctx = permissionCache.getOrFetchEffective(
                    session.accessToken(), me.userId(), me.organizationId(), me.membershipId());
            if (ctx != null) permissionCache.put(ctx);
        }
        return identity;
    }

    private static boolean isActive(String status) {
        return status != null && "ACTIVE".equalsIgnoreCase(status.trim());
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
