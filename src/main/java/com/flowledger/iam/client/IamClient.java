package com.flowledger.iam.client;

import com.flowledger.iam.config.IamProperties;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Orchestration client for Sankhya IAM. Token issuance always happens at Keycloak's token endpoint
 * (from {@link #resolve}), never at a custom IAM token issuer.
 */
public class IamClient {
    private final IamProperties properties;
    private final RestTemplate http;

    public IamClient(IamProperties properties, RestTemplate http) {
        this.properties = properties;
        this.http = http;
    }

    public IamDtos.AuthResolveResult resolve(String orgSlug, UUID organizationId, String clientId) {
        UriComponentsBuilder builder = base().path("/auth/resolve");
        if (StringUtils.hasText(orgSlug)) builder.queryParam("orgSlug", orgSlug);
        if (organizationId != null) builder.queryParam("organizationId", organizationId);
        if (StringUtils.hasText(clientId)) builder.queryParam("clientId", clientId);
        return http.getForObject(builder.build(true).toUri(), IamDtos.AuthResolveResult.class);
    }

    public IamDtos.LoginUrlResult loginUrl(
            String orgSlug,
            UUID organizationId,
            String clientId,
            String redirectUri,
            String state,
            String codeChallenge) {
        UriComponentsBuilder builder = base().path("/auth/login-url").queryParam("redirectUri", redirectUri);
        if (StringUtils.hasText(orgSlug)) builder.queryParam("orgSlug", orgSlug);
        if (organizationId != null) builder.queryParam("organizationId", organizationId);
        if (StringUtils.hasText(clientId)) builder.queryParam("clientId", clientId);
        if (StringUtils.hasText(state)) builder.queryParam("state", state);
        if (StringUtils.hasText(codeChallenge)) builder.queryParam("codeChallenge", codeChallenge);
        return http.getForObject(builder.build(true).toUri(), IamDtos.LoginUrlResult.class);
    }

    public String logoutUrl(
            String orgSlug,
            UUID organizationId,
            String clientId,
            String postLogoutRedirectUri,
            String idTokenHint) {
        UriComponentsBuilder builder = base().path("/auth/logout-url");
        if (StringUtils.hasText(orgSlug)) builder.queryParam("orgSlug", orgSlug);
        if (organizationId != null) builder.queryParam("organizationId", organizationId);
        if (StringUtils.hasText(clientId)) builder.queryParam("clientId", clientId);
        if (StringUtils.hasText(postLogoutRedirectUri)) {
            builder.queryParam("postLogoutRedirectUri", postLogoutRedirectUri);
        }
        if (StringUtils.hasText(idTokenHint)) builder.queryParam("idTokenHint", idTokenHint);
        IamDtos.LogoutUrlResponse body = http.getForObject(builder.build(true).toUri(), IamDtos.LogoutUrlResponse.class);
        return body == null ? null : body.logoutUrl();
    }

    public List<IamDtos.IssuerInfo> issuers() {
        ResponseEntity<IamDtos.IssuerInfo[]> response =
                http.getForEntity(base().path("/auth/issuers").build(true).toUri(), IamDtos.IssuerInfo[].class);
        IamDtos.IssuerInfo[] body = response.getBody();
        return body == null ? List.of() : Arrays.asList(body);
    }

    public IamDtos.MeResult provision(String accessToken, UUID organizationId, String orgSlug) {
        HttpHeaders headers = bearer(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        IamDtos.ProvisionRequest body = (organizationId == null && !StringUtils.hasText(orgSlug))
                ? null
                : new IamDtos.ProvisionRequest(organizationId, blankToNull(orgSlug));
        HttpEntity<IamDtos.ProvisionRequest> entity = new HttpEntity<>(body, headers);
        return http.postForObject(base().path("/auth/provision").build(true).toUri(), entity, IamDtos.MeResult.class);
    }

    /** @deprecated prefer {@link #provision(String, UUID, String)} */
    public IamDtos.MeResult provision(String accessToken, UUID organizationId) {
        return provision(accessToken, organizationId, null);
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    public IamDtos.MeResult me(String accessToken) {
        ResponseEntity<IamDtos.MeResult> response = http.exchange(
                base().path("/users/me").build(true).toUri(),
                HttpMethod.GET,
                new HttpEntity<>(bearer(accessToken)),
                IamDtos.MeResult.class);
        return response.getBody();
    }

    /**
     * Public self-service signup (no bearer). Forwards Idempotency-Key and X-Forwarded-For when set.
     */
    public IamDtos.SignupResult signup(Map<String, Object> body, String idempotencyKey, String clientIp) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (StringUtils.hasText(idempotencyKey)) {
            headers.set("Idempotency-Key", idempotencyKey.trim());
        }
        if (StringUtils.hasText(clientIp)) {
            headers.set("X-Forwarded-For", clientIp.trim());
        }
        return http.postForObject(
                base().path("/auth/signup").build(true).toUri(),
                new HttpEntity<>(body, headers),
                IamDtos.SignupResult.class);
    }

    public IamDtos.AvailabilityResult checkAvailability(String type, String value) {
        URI uri = base()
                .path("/auth/availability")
                .queryParam("type", type)
                .queryParam("value", value)
                .build(true)
                .toUri();
        return http.getForObject(uri, IamDtos.AvailabilityResult.class);
    }

    public IamDtos.AvailabilityProbeResult probeAvailability(String type, String value) {
        URI uri = base()
                .path("/auth/availability/probe")
                .queryParam("type", type)
                .queryParam("value", value)
                .build(true)
                .toUri();
        return http.getForObject(uri, IamDtos.AvailabilityProbeResult.class);
    }

    public List<IamDtos.MembershipView> listMyMemberships(String accessToken) {
        ResponseEntity<List<IamDtos.MembershipView>> response = http.exchange(
                base().path("/memberships/me").build(true).toUri(),
                HttpMethod.GET,
                new HttpEntity<>(bearer(accessToken)),
                new ParameterizedTypeReference<>() {});
        List<IamDtos.MembershipView> body = response.getBody();
        return body == null ? List.of() : body;
    }

    public IamDtos.EffectiveAuthorization effective(String accessToken, UUID organizationId) {
        return effective(accessToken, "ORGANIZATION", organizationId, properties.getProductCode());
    }

    public IamDtos.EffectiveAuthorization effective(
            String accessToken, String contextType, UUID organizationId, String productCode) {
        UriComponentsBuilder builder = base().path("/authorization/effective");
        if (StringUtils.hasText(contextType)) builder.queryParam("contextType", contextType);
        if (organizationId != null) builder.queryParam("organizationId", organizationId);
        if (StringUtils.hasText(productCode)) builder.queryParam("productCode", productCode);
        ResponseEntity<IamDtos.EffectiveAuthorization> response = http.exchange(
                builder.build(true).toUri(),
                HttpMethod.GET,
                new HttpEntity<>(bearer(accessToken)),
                IamDtos.EffectiveAuthorization.class);
        return response.getBody();
    }

    /**
     * Exchange authorization code at Keycloak (or IAM thin proxy). Not an IAM custom token issuer.
     */
    public IamDtos.TokenResponse exchangeAuthorizationCode(
            String tokenEndpoint,
            String clientId,
            String clientSecret,
            String code,
            String redirectUri,
            String codeVerifier) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", redirectUri);
        form.add("client_id", clientId);
        if (StringUtils.hasText(clientSecret)) form.add("client_secret", clientSecret);
        form.add("code_verifier", codeVerifier);
        return postToken(tokenEndpoint, form);
    }

    public IamDtos.TokenResponse refresh(
            String tokenEndpoint, String clientId, String clientSecret, String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        form.add("client_id", clientId);
        if (StringUtils.hasText(clientSecret)) form.add("client_secret", clientSecret);
        return postToken(tokenEndpoint, form);
    }

    private IamDtos.TokenResponse postToken(String tokenEndpoint, MultiValueMap<String, String> form) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        ResponseEntity<Map<String, Object>> response = http.exchange(
                URI.create(tokenEndpoint),
                HttpMethod.POST,
                new HttpEntity<>(form, headers),
                new ParameterizedTypeReference<>() {});
        Map<String, Object> body = response.getBody();
        if (body == null) {
            throw new IllegalStateException("Empty token response from Keycloak");
        }
        return new IamDtos.TokenResponse(
                string(body.get("access_token")),
                string(body.get("refresh_token")),
                string(body.get("id_token")),
                string(body.get("token_type")),
                body.get("expires_in") instanceof Number n ? n.longValue() : null,
                string(body.get("scope")));
    }

    private UriComponentsBuilder base() {
        String base = properties.getBaseUrl();
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return UriComponentsBuilder.fromUriString(base);
    }

    private static HttpHeaders bearer(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return headers;
    }

    private static String string(Object value) {
        return value == null ? null : value.toString();
    }
}
