package com.flowledger.iam.service;

import com.flowledger.auth.dto.LoginResponse;
import com.flowledger.auth.dto.OrganizationAccessResponse;
import com.flowledger.auth.dto.UserResponse;
import com.flowledger.auth.entity.User;
import com.flowledger.auth.service.OrganizationMembershipService;
import com.flowledger.common.exception.UnauthorizedException;
import com.flowledger.iam.client.IamClient;
import com.flowledger.iam.client.IamDtos;
import com.flowledger.iam.client.IamOidcEndpointResolver;
import com.flowledger.iam.config.IamProperties;
import com.flowledger.iam.security.IamAuthorityMapper;
import com.flowledger.iam.security.IamIdentityCache;
import com.flowledger.iam.security.IamOidcTokenValidator;
import com.flowledger.iam.security.IamPermissionCache;
import com.flowledger.iam.security.IamServerSessionStore;
import com.flowledger.ops.entity.PlatformUser;
import com.flowledger.ops.security.PlatformPrincipal;
import com.flowledger.ops.security.PlatformUserDetailsService;
import com.flowledger.organization.entity.Organization;
import com.flowledger.organization.repository.OrganizationRepository;
import com.nimbusds.jwt.JWTClaimsSet;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@Slf4j
public class IamAuthService {
    public static final String COOKIE_OAUTH = "fl_iam_oauth";
    public static final String COOKIE_SESSION = "fl_iam_session";
    public static final String COOKIE_OPS_OAUTH = "fl_iam_ops_oauth";
    public static final String COOKIE_OPS_SESSION = "fl_iam_ops_session";

    private final IamProperties properties;
    private final IamClient iamClient;
    private final IamOidcEndpointResolver endpointResolver;
    private final IamOidcTokenValidator tokenValidator;
    private final IamShellProvisioningService shells;
    private final IamPermissionCache permissionCache;
    private final IamAuthorityMapper authorityMapper;
    private final OrganizationMembershipService membershipService;
    private final PlatformUserDetailsService platformUsers;
    private final IamIdentityCache identityCache;
    private final IamServerSessionStore sessionStore;
    private final OrganizationRepository organizations;
    private final SecureRandom secureRandom = new SecureRandom();
    private final ConcurrentHashMap<String, ClaimTicket> claimTickets = new ConcurrentHashMap<>();
    /** Allows React Strict Mode / double POST to reclaim the same one-time code briefly. */
    private final ConcurrentHashMap<String, ClaimTicket> claimedTickets = new ConcurrentHashMap<>();

    public IamAuthService(
            IamProperties properties,
            IamClient iamClient,
            IamOidcEndpointResolver endpointResolver,
            IamOidcTokenValidator tokenValidator,
            IamShellProvisioningService shells,
            IamPermissionCache permissionCache,
            IamAuthorityMapper authorityMapper,
            OrganizationMembershipService membershipService,
            PlatformUserDetailsService platformUsers,
            IamIdentityCache identityCache,
            IamServerSessionStore sessionStore,
            OrganizationRepository organizations) {
        this.properties = properties;
        this.iamClient = iamClient;
        this.endpointResolver = endpointResolver;
        this.tokenValidator = tokenValidator;
        this.shells = shells;
        this.permissionCache = permissionCache;
        this.authorityMapper = authorityMapper;
        this.membershipService = membershipService;
        this.platformUsers = platformUsers;
        this.identityCache = identityCache;
        this.sessionStore = sessionStore;
        this.organizations = organizations;
    }

    public Map<String, Object> status() {
        // Invite-only is the default posture; self-service signup is opt-in on IAM
        // (sankhya.iam.self-service-enabled) and not enabled via FlowLedger BFF by default.
        return Map.of(
                "enabled",
                properties.isEnabled(),
                "productCode",
                properties.getProductCode(),
                "clientId",
                properties.getClientId(),
                "authMode",
                properties.isEnabled() ? "iam-session-cookie" : "legacy-hs256-rollback",
                "selfServiceSignupEnabled",
                properties.isSelfServiceSignupEnabled());
    }

    public IamDtos.SignupResult signup(Map<String, Object> body, String idempotencyKey, String clientIp) {
        if (!properties.isEnabled() || !properties.isSelfServiceSignupEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Self-service signup is disabled");
        }
        return iamClient.signup(body, idempotencyKey, clientIp);
    }

    public IamDtos.AvailabilityResult checkAvailability(String type, String value) {
        requireEnabled();
        return iamClient.checkAvailability(type, value);
    }

    public IamDtos.AvailabilityProbeResult probeAvailability(String type, String value) {
        requireEnabled();
        return iamClient.probeAvailability(type, value);
    }

    public Map<String, String> beginLogin(boolean ops, HttpServletResponse response) {
        requireEnabled();
        String clientId = ops ? properties.getOps().getClientId() : properties.getClientId();
        String redirectUri = ops ? properties.getOps().getRedirectUri() : properties.getRedirectUri();
        if (!StringUtils.hasText(redirectUri)) {
            throw new IllegalStateException("IAM redirect URI is not configured");
        }

        String state = randomUrlSafe(32);
        String nonce = randomUrlSafe(32);
        String codeVerifier = randomUrlSafe(64);
        String codeChallenge = s256Challenge(codeVerifier);

        OAuthState oauth =
                new OAuthState(state, nonce, codeVerifier, redirectUri, clientId, Instant.now().plusSeconds(600));
        writeCookie(response, ops ? COOKIE_OPS_OAUTH : COOKIE_OAUTH, encodeOAuth(oauth), 600, oauthCookiePath(ops));

        // orgSlug is bootstrap-only — never durable tenant identity
        IamOidcEndpointResolver.ResolvedLogin login = endpointResolver.beginAuthorize(
                blankToNull(properties.getOrgSlug()),
                properties.getOrganizationId(),
                clientId,
                redirectUri,
                state,
                nonce,
                codeChallenge);
        return Map.of("loginUrl", login.loginUrl(), "state", state, "path", login.path());
    }

    public String handleCallback(
            boolean ops, String code, String state, HttpServletRequest request, HttpServletResponse response) {
        requireEnabled();
        if (!StringUtils.hasText(code) || !StringUtils.hasText(state)) {
            throw new UnauthorizedException("Missing authorization code or state");
        }
        OAuthState oauth = decodeOAuth(readCookie(request, ops ? COOKIE_OPS_OAUTH : COOKIE_OAUTH));
        if (oauth == null || !state.equals(oauth.state()) || oauth.expiresAt().isBefore(Instant.now())) {
            throw new UnauthorizedException("Invalid or expired OAuth state");
        }
        String expectedRedirect = ops ? properties.getOps().getRedirectUri() : properties.getRedirectUri();
        String expectedClient = ops ? properties.getOps().getClientId() : properties.getClientId();
        if (!expectedRedirect.equals(oauth.redirectUri()) || !expectedClient.equals(oauth.clientId())) {
            throw new UnauthorizedException("OAuth client/redirect mismatch");
        }

        String clientSecret = ops ? properties.getOps().getClientSecret() : properties.getClientSecret();
        IamDtos.AuthResolveResult resolved = endpointResolver.resolveEndpoints(
                blankToNull(properties.getOrgSlug()), properties.getOrganizationId(), expectedClient);
        IamDtos.TokenResponse tokens = iamClient.exchangeAuthorizationCode(
                resolved.tokenEndpoint(),
                expectedClient,
                clientSecret,
                code,
                oauth.redirectUri(),
                oauth.codeVerifier());
        if (tokens == null || !StringUtils.hasText(tokens.accessToken())) {
            throw new UnauthorizedException("Token exchange failed");
        }
        JWTClaimsSet accessClaims = tokenValidator.validateAccessToken(tokens.accessToken());
        if (StringUtils.hasText(tokens.idToken())) {
            tokenValidator.validateIdToken(tokens.idToken(), oauth.nonce());
        }

        IamDtos.MeResult me = enrichIdentityAuthz(tokens.accessToken(), fetchIdentity(tokens.accessToken(), resolved));
        cacheAuthorization(tokens.accessToken(), me);

        Object sessionPayload = ops
                ? buildPlatformSessionMeta(tokens.accessToken(), me)
                : buildTenantSessionMeta(tokens.accessToken(), me);

        Instant accessExp = accessClaims.getExpirationTime() != null
                ? accessClaims.getExpirationTime().toInstant()
                : Instant.now().plusSeconds(1800);

        UUID localUserId;
        UUID localOrgId;
        String email;
        if (ops) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) sessionPayload;
            @SuppressWarnings("unchecked")
            Map<String, Object> user = (Map<String, Object>) map.get("user");
            localUserId = UUID.fromString(user.get("id").toString());
            localOrgId = me.organizationId();
            email = user.get("email").toString();
        } else {
            LoginResponse lr = (LoginResponse) sessionPayload;
            localUserId = lr.user().id();
            localOrgId = lr.activeOrganization().id();
            email = lr.user().email();
        }

        String sessionId = randomUrlSafe(32);
        sessionStore.put(
                sessionId,
                new IamServerSessionStore.Session(
                        ops,
                        tokens.accessToken(),
                        tokens.refreshToken(),
                        tokens.idToken(),
                        resolved.tokenEndpoint(),
                        expectedClient,
                        clientSecret,
                        me.userId(),
                        me.organizationId(),
                        me.membershipId(),
                        localUserId,
                        localOrgId,
                        email,
                        Instant.now(),
                        accessExp));

        clearCookie(response, ops ? COOKIE_OPS_OAUTH : COOKIE_OAUTH, oauthCookiePath(ops));
        writeCookie(
                response,
                ops ? COOKIE_OPS_SESSION : COOKIE_SESSION,
                sessionId,
                (int) (14L * 24 * 3600),
                sessionCookiePath(ops));

        String ticket = randomUrlSafe(32);
        claimTickets.put(ticket, new ClaimTicket(ops, sessionPayload, Instant.now().plusSeconds(120)));

        String successUrl = ops ? properties.getOps().getFrontendSuccessUrl() : properties.getFrontendSuccessUrl();
        return UriComponentsBuilder.fromUriString(successUrl)
                .queryParam("code", ticket)
                .build(true)
                .toUriString();
    }

    public Object claim(boolean ops, String code) {
        requireEnabled();
        if (!StringUtils.hasText(code)) {
            throw new UnauthorizedException("Invalid or expired session claim");
        }
        ClaimTicket ticket = claimTickets.remove(code);
        if (ticket == null) {
            ticket = claimedTickets.get(code);
        }
        if (ticket == null || ticket.ops() != ops || ticket.expiresAt().isBefore(Instant.now())) {
            throw new UnauthorizedException("Invalid or expired session claim");
        }
        claimedTickets.put(code, ticket);
        return ticket.payload();
    }

    public Object refresh(boolean ops, HttpServletRequest request, HttpServletResponse response) {
        requireEnabled();
        String sessionId = readCookie(request, ops ? COOKIE_OPS_SESSION : COOKIE_SESSION);
        IamServerSessionStore.Session session = sessionStore.get(sessionId);
        if (session == null || session.ops() != ops || !StringUtils.hasText(session.refreshToken())) {
            throw new UnauthorizedException("Missing IAM session");
        }
        IamDtos.TokenResponse tokens = iamClient.refresh(
                session.tokenEndpoint(), session.clientId(), session.clientSecret(), session.refreshToken());
        JWTClaimsSet claims = tokenValidator.validateAccessToken(tokens.accessToken());
        Instant accessExp = claims.getExpirationTime() != null
                ? claims.getExpirationTime().toInstant()
                : Instant.now().plusSeconds(1800);
        sessionStore.put(
                sessionId,
                session.withTokens(tokens.accessToken(), tokens.refreshToken(), tokens.idToken(), accessExp));

        IamDtos.MeResult me = enrichIdentityAuthz(tokens.accessToken(), fetchIdentity(tokens.accessToken(), null));
        // Keep session org/membership; only tokens were refreshed. Re-cache authz for the active triple.
        IamServerSessionStore.Session refreshed = sessionStore.get(sessionId);
        if (refreshed != null && refreshed.iamMembershipId() != null) {
            me = overlayActiveOrg(me, refreshed.iamOrganizationId(), refreshed.iamMembershipId());
            cacheAuthorization(tokens.accessToken(), me);
        } else {
            cacheAuthorization(tokens.accessToken(), me);
            if (me.membershipId() != null && refreshed != null) {
                sessionStore.put(
                        sessionId,
                        refreshed.withActiveOrganization(me.organizationId(), me.membershipId(), refreshed.localOrgId()));
            }
        }
        writeCookie(
                response,
                ops ? COOKIE_OPS_SESSION : COOKIE_SESSION,
                sessionId,
                (int) (14L * 24 * 3600),
                sessionCookiePath(ops));
        return ops ? buildPlatformSessionMeta(tokens.accessToken(), me) : buildTenantSessionMeta(tokens.accessToken(), me);
    }

    public List<OrganizationAccessResponse> listOrganizations(HttpServletRequest request) {
        requireEnabled();
        IamServerSessionStore.Session session = requireTenantSession(request);
        List<IamDtos.MembershipView> memberships = iamClient.listMyMemberships(session.accessToken());
        List<OrganizationAccessResponse> result = new ArrayList<>();
        for (IamDtos.MembershipView m : memberships) {
            if (m == null || m.organizationId() == null) continue;
            if (!isActive(m.status())) continue;
            IamDtos.MeResult shellMe = new IamDtos.MeResult(
                    m.userId() != null ? m.userId() : session.iamUserId(),
                    null,
                    m.email() != null ? m.email() : session.email(),
                    m.displayName(),
                    m.organizationId(),
                    m.organizationName(),
                    m.membershipId(),
                    null,
                    m.roles() == null ? List.of() : m.roles(),
                    List.of(),
                    List.of(),
                    List.of(),
                    null);
            IamShellProvisioningService.ShellResult shell = shells.provisionTenant(shellMe);
            result.add(new OrganizationAccessResponse(
                    shell.organization().getId(),
                    shell.organization().getName(),
                    Set.copyOf(m.roles() == null ? List.of() : m.roles()),
                    m.status() != null ? m.status() : "ACTIVE",
                    shell.organization().isOnboardingCompleted()));
        }
        return result;
    }

    public LoginResponse switchOrganization(UUID organizationId, HttpServletRequest request) {
        requireEnabled();
        String sessionId = readCookie(request, COOKIE_SESSION);
        IamServerSessionStore.Session session = sessionStore.get(sessionId);
        if (session == null || session.ops() || !StringUtils.hasText(session.accessToken())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing IAM session");
        }

        Organization localOrg = organizations
                .findById(organizationId)
                .or(() -> organizations.findByIamOrganizationId(organizationId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Organization not found"));
        UUID targetIamOrgId = localOrg.getIamOrganizationId();
        if (targetIamOrgId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Organization is not linked to IAM");
        }

        IamDtos.MembershipView membership;
        try {
            membership = iamClient.listMyMemberships(session.accessToken()).stream()
                    .filter(m -> m != null
                            && targetIamOrgId.equals(m.organizationId())
                            && isActive(m.status())
                            && m.membershipId() != null)
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unable to validate IAM membership");
        }
        if (membership == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No ACTIVE membership for organization");
        }

        IamPermissionCache.AuthorizationContext authzB;
        try {
            IamDtos.EffectiveAuthorization effective =
                    iamClient.effective(session.accessToken(), targetIamOrgId);
            if (effective == null) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Effective authorization unavailable");
            }
            if (!isActive(effective.userStatus())
                    || !isActive(effective.organizationStatus())
                    || !isActive(effective.membershipStatus())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "IAM user/organization/membership not ACTIVE");
            }
            if (effective.membershipId() != null && !membership.membershipId().equals(effective.membershipId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Membership mismatch");
            }
            authzB = permissionCache.fromEffective(effective);
            if (authzB.iamMembershipId() == null) {
                authzB = new IamPermissionCache.AuthorizationContext(
                        authzB.iamUserId() != null ? authzB.iamUserId() : session.iamUserId(),
                        targetIamOrgId,
                        membership.membershipId(),
                        authzB.organizationStatus(),
                        authzB.membershipStatus(),
                        authzB.userStatus(),
                        authzB.roles(),
                        authzB.permissions(),
                        authzB.productAccess(),
                        authzB.issuedAt(),
                        authzB.expiresAt());
            }
            if (authzB.iamUserId() == null
                    || authzB.iamOrganizationId() == null
                    || authzB.iamMembershipId() == null) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Incomplete authorization context");
            }
            // put B → evict A → only then update session
            permissionCache.put(authzB);
            if (session.iamUserId() != null
                    && session.iamOrganizationId() != null
                    && session.iamMembershipId() != null
                    && !(session.iamOrganizationId().equals(targetIamOrgId)
                            && session.iamMembershipId().equals(membership.membershipId()))) {
                permissionCache.evict(session.iamUserId(), session.iamOrganizationId(), session.iamMembershipId());
            }
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Organization switch denied");
        }

        IamServerSessionStore.Session updated =
                session.withActiveOrganization(targetIamOrgId, membership.membershipId(), localOrg.getId());
        sessionStore.put(sessionId, updated);

        IamDtos.MeResult me = meFromAuthz(updated, membership, authzB, localOrg);
        LoginResponse meta = buildTenantSessionMeta(updated.accessToken(), me);
        List<OrganizationAccessResponse> iamOrgs = listOrganizations(request);
        if (!iamOrgs.isEmpty()) {
            return new LoginResponse(
                    meta.accessToken(),
                    meta.refreshToken(),
                    meta.expiresIn(),
                    meta.user(),
                    meta.activeOrganization(),
                    iamOrgs);
        }
        return meta;
    }

    public Map<String, Object> currentContext(HttpServletRequest request) {
        requireEnabled();
        IamServerSessionStore.Session session = requireTenantSession(request);
        IamPermissionCache.AuthorizationContext authz = null;
        if (session.iamUserId() != null
                && session.iamOrganizationId() != null
                && session.iamMembershipId() != null) {
            authz = permissionCache.get(
                    session.iamUserId(), session.iamOrganizationId(), session.iamMembershipId());
            if (authz == null) {
                authz = permissionCache.getOrFetchEffective(
                        session.accessToken(),
                        session.iamUserId(),
                        session.iamOrganizationId(),
                        session.iamMembershipId());
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("iamUserId", session.iamUserId());
        body.put("iamOrganizationId", session.iamOrganizationId());
        body.put("iamMembershipId", session.iamMembershipId());
        body.put("localUserId", session.localUserId());
        body.put("localOrganizationId", session.localOrgId());
        body.put("email", session.email());
        if (authz != null) {
            body.put("userStatus", authz.userStatus());
            body.put("organizationStatus", authz.organizationStatus());
            body.put("membershipStatus", authz.membershipStatus());
            body.put("roles", authz.roles());
            body.put("permissions", authz.permissions());
            body.put("productAccess", authz.productAccess());
        }
        if (session.localUserId() != null && session.localOrgId() != null) {
            try {
                IamDtos.MeResult me = iamClient.me(session.accessToken());
                if (me != null) {
                    me = overlayActiveOrg(me, session.iamOrganizationId(), session.iamMembershipId());
                    LoginResponse meta = buildTenantSessionMeta(session.accessToken(), me);
                    body.put("session", meta);
                }
            } catch (Exception e) {
                log.debug("IAM context session meta unavailable: {}", e.getMessage());
            }
        }
        return body;
    }

    public Map<String, String> logoutUrl(boolean ops, HttpServletRequest request, HttpServletResponse response) {
        String sessionId = readCookie(request, ops ? COOKIE_OPS_SESSION : COOKIE_SESSION);
        IamServerSessionStore.Session session = sessionStore.get(sessionId);
        String idTokenHint = session != null ? session.idToken() : null;
        sessionStore.remove(sessionId);
        clearCookie(response, ops ? COOKIE_OPS_SESSION : COOKIE_SESSION, sessionCookiePath(ops));
        clearCookie(response, ops ? COOKIE_OPS_OAUTH : COOKIE_OAUTH, oauthCookiePath(ops));

        String clientId = ops ? properties.getOps().getClientId() : properties.getClientId();
        String postLogout = ops
                ? properties.getOps().getPostLogoutRedirectUri()
                : properties.getPostLogoutRedirectUri();
        String url;
        try {
            url = iamClient.logoutUrl(
                    blankToNull(properties.getOrgSlug()),
                    properties.getOrganizationId(),
                    clientId,
                    postLogout,
                    idTokenHint);
        } catch (Exception e) {
            log.warn("IAM logout-url failed: {}", e.getMessage());
            url = null;
        }
        Map<String, String> body = new LinkedHashMap<>();
        body.put("logoutUrl", url == null ? postLogout : url);
        return body;
    }

    /** Tenant claim/refresh payload — no Keycloak tokens exposed to the browser. */
    public LoginResponse buildTenantSessionMeta(String accessToken, IamDtos.MeResult me) {
        IamShellProvisioningService.ShellResult shell = shells.provisionTenant(me);
        User user = shell.user();
        Organization org = shell.organization();
        String subject = StringUtils.hasText(me.keycloakUserId()) ? me.keycloakUserId() : accessClaimsSubject(accessToken);
        identityCache.put(
                subject,
                new IamIdentityCache.Identity(
                        user.getId(), org.getId(), me.userId(), me.organizationId(), user.getEmail(), false));
        OrganizationAccessResponse active = new OrganizationAccessResponse(
                org.getId(),
                org.getName(),
                Set.copyOf(me.roles() == null ? List.of() : me.roles()),
                "ACTIVE",
                org.isOnboardingCompleted());
        List<OrganizationAccessResponse> orgs = membershipService.listAccessibleOrganizations(user.getId());
        if (orgs.stream().noneMatch(o -> o.id().equals(org.getId()))) {
            orgs = List.of(active);
        }
        return new LoginResponse(
                "",
                "",
                expiresIn(accessToken),
                new UserResponse(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(), user.getUserStatus()),
                active,
                orgs);
    }

    public Map<String, Object> buildPlatformSessionMeta(String accessToken, IamDtos.MeResult me) {
        PlatformUser platformUser = shells.provisionPlatform(me);
        PlatformPrincipal principal = platformUsers.toPrincipal(platformUser);
        String subject = StringUtils.hasText(me.keycloakUserId()) ? me.keycloakUserId() : accessClaimsSubject(accessToken);
        identityCache.put(
                subject,
                new IamIdentityCache.Identity(
                        platformUser.getId(),
                        me.organizationId(),
                        me.userId(),
                        me.organizationId(),
                        platformUser.getEmail(),
                        true));
        var authorities = authorityMapper.toAuthorities(me.roles(), me.permissions());
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", principal.getId());
        user.put("email", principal.getEmail());
        user.put("firstName", platformUser.getFirstName());
        user.put("lastName", platformUser.getLastName());
        user.put("roles", me.roles() == null ? principal.getRoles() : me.roles());
        user.put("permissions", me.permissions() == null ? principal.getPermissions() : me.permissions());
        user.put("authorities", authorities);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("accessToken", "");
        body.put("refreshToken", "");
        body.put("expiresIn", expiresIn(accessToken));
        body.put("user", user);
        body.put("authMode", "iam");
        return body;
    }

    public IamServerSessionStore.Session loadSession(HttpServletRequest request, boolean ops) {
        String sessionId = readCookie(request, ops ? COOKIE_OPS_SESSION : COOKIE_SESSION);
        return sessionStore.get(sessionId);
    }

    public IamServerSessionStore.Session ensureFreshAccess(HttpServletRequest request, boolean ops) {
        String sessionId = readCookie(request, ops ? COOKIE_OPS_SESSION : COOKIE_SESSION);
        IamServerSessionStore.Session session = sessionStore.get(sessionId);
        if (session == null || session.ops() != ops) return null;
        if (session.accessExpiresAt() != null
                && session.accessExpiresAt().isAfter(Instant.now().plusSeconds(60))) {
            return session;
        }
        if (!StringUtils.hasText(session.refreshToken())) return session;
        try {
            IamDtos.TokenResponse tokens = iamClient.refresh(
                    session.tokenEndpoint(), session.clientId(), session.clientSecret(), session.refreshToken());
            JWTClaimsSet claims = tokenValidator.validateAccessToken(tokens.accessToken());
            Instant accessExp = claims.getExpirationTime() != null
                    ? claims.getExpirationTime().toInstant()
                    : Instant.now().plusSeconds(1800);
            IamServerSessionStore.Session updated =
                    session.withTokens(tokens.accessToken(), tokens.refreshToken(), tokens.idToken(), accessExp);
            sessionStore.put(sessionId, updated);
            return updated;
        } catch (Exception e) {
            log.debug("Silent IAM session refresh failed: {}", e.getMessage());
            return session;
        }
    }

    private String accessClaimsSubject(String accessToken) {
        try {
            return tokenValidator.parseUnvalidated(accessToken).getSubject();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * When IAM returns an empty authZ snapshot (common before product roles are seeded), fetch
     * /authorization/effective and, if still empty, apply a temporary org-admin bootstrap so local
     * smoke can proceed. Replace with real IAM grants before production cutover.
     */
    private IamDtos.MeResult enrichIdentityAuthz(String accessToken, IamDtos.MeResult me) {
        if (me == null) return null;
        boolean hasAuthz = (me.roles() != null && !me.roles().isEmpty())
                || (me.permissions() != null && !me.permissions().isEmpty());
        IamDtos.MeResult current = me;
        if (!hasAuthz || me.membershipId() == null) {
            try {
                IamDtos.EffectiveAuthorization effective =
                        iamClient.effective(accessToken, me.organizationId());
                if (effective != null
                        && ((effective.roles() != null && !effective.roles().isEmpty())
                                || (effective.permissions() != null && !effective.permissions().isEmpty())
                                || effective.membershipId() != null)) {
                    current = withEffective(me, effective);
                    hasAuthz = (current.roles() != null && !current.roles().isEmpty())
                            || (current.permissions() != null && !current.permissions().isEmpty());
                }
            } catch (Exception e) {
                log.debug("IAM effective authorization unavailable: {}", e.getMessage());
            }
        }
        if (!hasAuthz) {
            log.warn(
                    "IAM authZ empty for user {} org {}; applying temporary ORGANIZATION_ADMIN bootstrap",
                    me.userId(),
                    me.organizationId());
            current = new IamDtos.MeResult(
                    me.userId(),
                    me.keycloakUserId(),
                    me.email(),
                    me.displayName(),
                    me.organizationId(),
                    me.organizationName(),
                    me.membershipId(),
                    me.subscriptionPlan(),
                    List.of("ORGANIZATION_ADMIN"),
                    List.of(
                            "AI_CHAT",
                            "flowledger.ai.chat",
                            "AI_ADMIN",
                            "SALES_READ",
                            "SALES_WRITE",
                            "PRODUCT_READ",
                            "PRODUCT_WRITE"),
                    me.productAccess() == null ? List.of() : me.productAccess(),
                    me.featureFlags() == null ? List.of() : me.featureFlags(),
                    me.onboardingStatus());
        }
        return current;
    }

    private void cacheAuthorization(String accessToken, IamDtos.MeResult me) {
        if (me == null || me.userId() == null || me.organizationId() == null) return;
        if (me.membershipId() != null) {
            IamPermissionCache.AuthorizationContext ctx = permissionCache.getOrFetchEffective(
                    accessToken, me.userId(), me.organizationId(), me.membershipId());
            if (ctx != null) {
                permissionCache.put(ctx);
                return;
            }
        }
        try {
            IamDtos.EffectiveAuthorization effective = iamClient.effective(accessToken, me.organizationId());
            IamPermissionCache.AuthorizationContext ctx = permissionCache.fromEffective(effective);
            if (ctx != null) permissionCache.put(ctx);
        } catch (Exception e) {
            log.debug("Unable to cache IAM authorization: {}", e.getMessage());
        }
    }

    private static IamDtos.MeResult withEffective(IamDtos.MeResult me, IamDtos.EffectiveAuthorization effective) {
        return new IamDtos.MeResult(
                me.userId(),
                me.keycloakUserId(),
                me.email(),
                me.displayName(),
                me.organizationId(),
                me.organizationName(),
                effective.membershipId() != null ? effective.membershipId() : me.membershipId(),
                effective.subscriptionPlan() != null ? effective.subscriptionPlan() : me.subscriptionPlan(),
                effective.roles() != null ? effective.roles() : List.of(),
                effective.permissions() != null ? effective.permissions() : List.of(),
                effective.productAccess() != null
                        ? effective.productAccess()
                        : (me.productAccess() != null ? me.productAccess() : List.of()),
                effective.featureFlags() != null ? effective.featureFlags() : me.featureFlags(),
                me.onboardingStatus());
    }

    private static IamDtos.MeResult overlayActiveOrg(IamDtos.MeResult me, UUID iamOrgId, UUID iamMembershipId) {
        if (me == null) return null;
        return new IamDtos.MeResult(
                me.userId(),
                me.keycloakUserId(),
                me.email(),
                me.displayName(),
                iamOrgId != null ? iamOrgId : me.organizationId(),
                me.organizationName(),
                iamMembershipId != null ? iamMembershipId : me.membershipId(),
                me.subscriptionPlan(),
                me.roles(),
                me.permissions(),
                me.productAccess(),
                me.featureFlags(),
                me.onboardingStatus());
    }

    private static IamDtos.MeResult overlayMe(IamDtos.MeResult base, IamDtos.MeResult authz) {
        return new IamDtos.MeResult(
                base.userId(),
                base.keycloakUserId(),
                base.email(),
                base.displayName(),
                authz.organizationId() != null ? authz.organizationId() : base.organizationId(),
                authz.organizationName() != null ? authz.organizationName() : base.organizationName(),
                authz.membershipId() != null ? authz.membershipId() : base.membershipId(),
                authz.subscriptionPlan() != null ? authz.subscriptionPlan() : base.subscriptionPlan(),
                authz.roles() != null ? authz.roles() : base.roles(),
                authz.permissions() != null ? authz.permissions() : base.permissions(),
                authz.productAccess() != null ? authz.productAccess() : base.productAccess(),
                authz.featureFlags() != null ? authz.featureFlags() : base.featureFlags(),
                base.onboardingStatus());
    }

    private static IamDtos.MeResult meFromAuthz(
            IamServerSessionStore.Session session,
            IamDtos.MembershipView membership,
            IamPermissionCache.AuthorizationContext authz,
            Organization localOrg) {
        return new IamDtos.MeResult(
                session.iamUserId(),
                null,
                session.email(),
                membership.displayName(),
                authz.iamOrganizationId(),
                localOrg.getName(),
                authz.iamMembershipId(),
                null,
                authz.roles(),
                authz.permissions(),
                authz.productAccess(),
                List.of(),
                null);
    }

    private IamServerSessionStore.Session requireTenantSession(HttpServletRequest request) {
        IamServerSessionStore.Session session = ensureFreshAccess(request, false);
        if (session == null || session.ops() || !StringUtils.hasText(session.accessToken())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing IAM session");
        }
        return session;
    }

    private static boolean isActive(String status) {
        return status != null && "ACTIVE".equalsIgnoreCase(status.trim());
    }

    private IamDtos.MeResult fetchIdentity(String accessToken, IamDtos.AuthResolveResult resolved) {
        UUID organizationId = properties.getOrganizationId();
        if (organizationId == null && resolved != null) {
            organizationId = resolved.organizationId();
        }
        if (organizationId == null) {
            organizationId = organizationIdFromToken(accessToken);
        }
        String orgSlug = blankToNull(properties.getOrgSlug());
        try {
            IamDtos.MeResult provisioned = iamClient.provision(accessToken, organizationId, orgSlug);
            if (provisioned != null && provisioned.userId() != null && provisioned.organizationId() != null) {
                return provisioned;
            }
            if (provisioned != null) {
                log.warn(
                        "IAM provision incomplete userId={} organizationId={} (hintOrgId={} orgSlug={})",
                        provisioned.userId(),
                        provisioned.organizationId(),
                        organizationId,
                        orgSlug);
            }
        } catch (Exception e) {
            log.debug("IAM provision failed, falling back to /users/me: {}", e.getMessage());
        }
        IamDtos.MeResult me = iamClient.me(accessToken);
        if (me == null || me.userId() == null || me.organizationId() == null) {
            throw new UnauthorizedException(
                    "Unable to resolve IAM user profile with organizationId (set FLOWLEDGER_IAM_ORGANIZATION_ID or orgSlug, or map organizationId claim)");
        }
        return me;
    }

    private UUID organizationIdFromToken(String accessToken) {
        try {
            JWTClaimsSet claims = tokenValidator.parseUnvalidated(accessToken);
            String raw = claims.getStringClaim("organizationId");
            if (!StringUtils.hasText(raw)) {
                Object attr = claims.getClaim("organizationId");
                if (attr instanceof List<?> list && !list.isEmpty()) {
                    raw = String.valueOf(list.get(0));
                }
            }
            if (StringUtils.hasText(raw)) {
                return UUID.fromString(raw.trim());
            }
        } catch (Exception ignored) {
            // claim optional
        }
        return null;
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) {
            throw new UnauthorizedException("IAM SSO is disabled");
        }
    }

    private String oauthCookiePath(boolean ops) {
        return ops ? "/api/v1/ops/auth/iam" : "/api/v1/auth/iam";
    }

    private String sessionCookiePath(boolean ops) {
        return ops ? "/api/v1/ops" : "/api/v1";
    }

    private void writeCookie(HttpServletResponse response, String name, String value, int maxAge, String path) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(properties.isCookieSecure())
                .path(path)
                .sameSite(properties.isCookieSecure() ? "None" : "Lax")
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearCookie(HttpServletResponse response, String name, String path) {
        ResponseCookie cookie = ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(properties.isCookieSecure())
                .path(path)
                .sameSite(properties.isCookieSecure() ? "None" : "Lax")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private static String readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }

    private static String encodeOAuth(OAuthState state) {
        String raw = state.state()
                + "|"
                + state.nonce()
                + "|"
                + state.codeVerifier()
                + "|"
                + state.redirectUri()
                + "|"
                + state.clientId()
                + "|"
                + state.expiresAt().getEpochSecond();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static OAuthState decodeOAuth(String value) {
        if (!StringUtils.hasText(value)) return null;
        try {
            String raw = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|", 6);
            if (parts.length != 6) return null;
            return new OAuthState(
                    parts[0],
                    parts[1],
                    parts[2],
                    parts[3],
                    parts[4],
                    Instant.ofEpochSecond(Long.parseLong(parts[5])));
        } catch (Exception e) {
            return null;
        }
    }

    private String randomUrlSafe(int bytes) {
        byte[] buf = new byte[bytes];
        secureRandom.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    private static String s256Challenge(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private long expiresIn(String accessToken) {
        try {
            JWTClaimsSet claims = tokenValidator.parseUnvalidated(accessToken);
            if (claims.getExpirationTime() == null) return 1800;
            return Math.max(
                    0, claims.getExpirationTime().toInstant().getEpochSecond() - Instant.now().getEpochSecond());
        } catch (Exception e) {
            return 1800;
        }
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private record OAuthState(
            String state, String nonce, String codeVerifier, String redirectUri, String clientId, Instant expiresAt) {}

    private record ClaimTicket(boolean ops, Object payload, Instant expiresAt) {}
}
