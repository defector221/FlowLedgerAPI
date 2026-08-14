package com.flowledger.iam.client;

import com.flowledger.iam.config.IamProperties;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Resolves OIDC endpoints: prefer IAM login-url/resolve; on failure use /auth/issuers + Keycloak
 * discovery with explicit trusted-issuer allowlist.
 */
@Slf4j
@Component
public class IamOidcEndpointResolver {
    private final IamProperties properties;
    private final IamClient iamClient;
    private final RestTemplate http;

    public IamOidcEndpointResolver(IamProperties properties, IamClient iamClient, RestTemplate iamRestTemplate) {
        this.properties = properties;
        this.iamClient = iamClient;
        this.http = iamRestTemplate;
    }

    public ResolvedLogin beginAuthorize(
            String orgSlug,
            UUID organizationId,
            String clientId,
            String redirectUri,
            String state,
            String nonce,
            String codeChallenge) {
        try {
            IamDtos.LoginUrlResult login =
                    iamClient.loginUrl(orgSlug, organizationId, clientId, redirectUri, state, codeChallenge);
            if (login != null && StringUtils.hasText(login.loginUrl())) {
                String loginUrl = ensureNonce(login.loginUrl(), nonce);
                IamDtos.AuthResolveResult resolved = softResolve(orgSlug, organizationId, clientId);
                log.info("IAM SSO login path=iam-login-url clientId={}", clientId);
                return new ResolvedLogin(loginUrl, resolved, "iam-login-url");
            }
        } catch (Exception e) {
            log.warn("IAM login-url failed ({}), trying Keycloak fallback", e.getMessage());
            if (!properties.isFallbackDirectKeycloak()) {
                throw e instanceof RuntimeException re ? re : new IllegalStateException(e);
            }
        }
        return buildFallbackLogin(orgSlug, organizationId, clientId, redirectUri, state, nonce, codeChallenge);
    }

    public IamDtos.AuthResolveResult resolveEndpoints(String orgSlug, UUID organizationId, String clientId) {
        try {
            IamDtos.AuthResolveResult resolved = iamClient.resolve(orgSlug, organizationId, clientId);
            if (resolved != null && StringUtils.hasText(resolved.tokenEndpoint())) {
                assertIssuerAllowed(resolved.issuer());
                return resolved;
            }
        } catch (Exception e) {
            log.warn("IAM resolve failed ({}), trying Keycloak fallback", e.getMessage());
            if (!properties.isFallbackDirectKeycloak()) {
                throw e instanceof RuntimeException re ? re : new IllegalStateException(e);
            }
        }
        return discoverFromAllowlist(clientId);
    }

    private ResolvedLogin buildFallbackLogin(
            String orgSlug,
            UUID organizationId,
            String clientId,
            String redirectUri,
            String state,
            String nonce,
            String codeChallenge) {
        IamDtos.AuthResolveResult resolved = discoverFromAllowlist(clientId);
        String loginUrl = UriComponentsBuilder.fromUriString(resolved.authorizationEndpoint())
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", "openid profile email")
                .queryParam("state", state)
                .queryParam("nonce", nonce)
                .queryParam("code_challenge", codeChallenge)
                .queryParam("code_challenge_method", "S256")
                .encode(StandardCharsets.UTF_8)
                .build()
                .toUriString();
        log.info("IAM SSO login path=keycloak-fallback clientId={} issuer={}", clientId, resolved.issuer());
        return new ResolvedLogin(loginUrl, resolved, "keycloak-fallback");
    }

    private IamDtos.AuthResolveResult softResolve(String orgSlug, UUID organizationId, String clientId) {
        try {
            return resolveEndpoints(orgSlug, organizationId, clientId);
        } catch (Exception e) {
            return discoverFromAllowlist(clientId);
        }
    }

    private IamDtos.AuthResolveResult discoverFromAllowlist(String clientId) {
        String issuer = selectAllowedIssuer();
        assertIssuerAllowed(issuer);
        Map<String, Object> discovery = fetchDiscovery(issuer);
        String auth = string(discovery.get("authorization_endpoint"));
        String token = string(discovery.get("token_endpoint"));
        String jwks = string(discovery.get("jwks_uri"));
        String end = string(discovery.get("end_session_endpoint"));
        if (!StringUtils.hasText(auth) || !StringUtils.hasText(token)) {
            throw new IllegalStateException("OIDC discovery missing authorization/token endpoints for " + issuer);
        }
        String realm = issuer.substring(issuer.lastIndexOf('/') + 1);
        return new IamDtos.AuthResolveResult(issuer, auth, token, jwks, end, realm, clientId, true, null);
    }

    private String selectAllowedIssuer() {
        List<String> allow = properties.getTrustedIssuers();
        if (allow == null || allow.isEmpty()) {
            throw new IllegalStateException("flowledger.iam.trusted-issuers allowlist is empty");
        }
        try {
            List<IamDtos.IssuerInfo> fromIam = iamClient.issuers();
            for (IamDtos.IssuerInfo info : fromIam) {
                if (info != null && allow.contains(info.issuer())) {
                    return info.issuer();
                }
            }
        } catch (Exception e) {
            log.debug("IAM issuers unavailable during fallback: {}", e.getMessage());
        }
        return allow.get(0);
    }

    public void assertIssuerAllowed(String issuer) {
        List<String> allow = properties.getTrustedIssuers();
        if (allow == null || allow.isEmpty()) {
            throw new IllegalArgumentException("No trusted issuers configured");
        }
        if (!StringUtils.hasText(issuer) || !allow.contains(issuer)) {
            throw new IllegalArgumentException("Untrusted issuer (not on allowlist): " + issuer);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchDiscovery(String issuer) {
        String normalized = issuer.endsWith("/") ? issuer.substring(0, issuer.length() - 1) : issuer;
        String url = normalized + "/.well-known/openid-configuration";
        try {
            ResponseEntity<Map<String, Object>> response =
                    http.exchange(url, HttpMethod.GET, null, new ParameterizedTypeReference<>() {});
            Map<String, Object> body = response.getBody();
            if (body == null) throw new IllegalStateException("Empty OIDC discovery");
            return body;
        } catch (RestClientException e) {
            throw new IllegalStateException("OIDC discovery failed for " + issuer + ": " + e.getMessage(), e);
        }
    }

    private static String ensureNonce(String loginUrl, String nonce) {
        if (loginUrl.contains("nonce=")) return loginUrl;
        return loginUrl + (loginUrl.contains("?") ? "&" : "?") + "nonce=" + nonce;
    }

    private static String string(Object value) {
        return value == null ? null : value.toString();
    }

    public record ResolvedLogin(String loginUrl, IamDtos.AuthResolveResult endpoints, String path) {}
}
