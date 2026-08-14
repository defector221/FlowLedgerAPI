package com.flowledger.iam.security;

import com.flowledger.iam.client.IamClient;
import com.flowledger.iam.client.IamDtos;
import com.flowledger.iam.config.IamProperties;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import java.net.URL;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Validates Keycloak-issued access/id tokens using iss/aud/sig(JWKS)/exp/nbf.
 */
@Slf4j
@Component
public class IamOidcTokenValidator {
    private final IamProperties properties;
    private final IamClient iamClient;
    private final Map<String, CachedJwks> jwksByUri = new ConcurrentHashMap<>();
    private volatile Set<String> trustedIssuers = Set.of();
    private volatile Instant issuersLoadedAt = Instant.EPOCH;

    public IamOidcTokenValidator(IamProperties properties, IamClient iamClient) {
        this.properties = properties;
        this.iamClient = iamClient;
    }

    public JWTClaimsSet validateAccessToken(String token) {
        return validate(token, true);
    }

    public JWTClaimsSet validateIdToken(String token, String expectedNonce) {
        JWTClaimsSet claims = validate(token, false);
        Object nonce = claims.getClaim("nonce");
        if (!StringUtils.hasText(expectedNonce) || nonce == null || !expectedNonce.equals(nonce.toString())) {
            throw new IllegalArgumentException("ID token nonce mismatch");
        }
        return claims;
    }

    public JWTClaimsSet parseUnvalidated(String token) {
        try {
            return SignedJWT.parse(token).getJWTClaimsSet();
        } catch (Exception e) {
            throw new IllegalArgumentException("Malformed JWT", e);
        }
    }

    private JWTClaimsSet validate(String token, boolean requireAudienceWhenConfigured) {
        try {
            SignedJWT signed = SignedJWT.parse(token);
            String issuer = signed.getJWTClaimsSet().getIssuer();
            ensureTrustedIssuers();
            if (!trustedIssuers.contains(issuer)) {
                // Allow issuer if present in /auth/issuers after refresh
                refreshIssuers();
                if (!trustedIssuers.contains(issuer)) {
                    throw new IllegalArgumentException("Untrusted issuer: " + issuer);
                }
            }
            String jwksUri = resolveJwksUri(issuer);
            ConfigurableJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
            DefaultResourceRetriever retriever = new DefaultResourceRetriever(5000, 5000);
            JWKSource<SecurityContext> keySource = jwksSource(jwksUri, retriever);
            JWSKeySelector<SecurityContext> keySelector =
                    new JWSVerificationKeySelector<>(Set.of(JWSAlgorithm.RS256, JWSAlgorithm.ES256), keySource);
            processor.setJWSKeySelector(keySelector);
            JWTClaimsSet claims = processor.process(signed, null);

            Date now = new Date();
            Date exp = claims.getExpirationTime();
            if (exp == null || !exp.after(now)) {
                throw new IllegalArgumentException("Token expired");
            }
            Date nbf = claims.getNotBeforeTime();
            if (nbf != null && nbf.after(Date.from(Instant.now().plusSeconds(60)))) {
                throw new IllegalArgumentException("Token not yet valid");
            }
            if (requireAudienceWhenConfigured && StringUtils.hasText(properties.getAudience())) {
                List<String> audiences = claims.getAudience();
                if (audiences == null || audiences.stream().noneMatch(a -> properties.getAudience().equals(a))) {
                    // Keycloak often places client_id in azp for access tokens
                    Object azp = claims.getClaim("azp");
                    if (azp == null || !properties.getAudience().equals(azp.toString())) {
                        throw new IllegalArgumentException("Audience mismatch");
                    }
                }
            }
            return claims;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("OIDC token validation failed: " + e.getMessage(), e);
        }
    }

    private synchronized void ensureTrustedIssuers() {
        Instant expiry = issuersLoadedAt.plusSeconds(properties.getJwksCacheTtlSeconds());
        if (trustedIssuers.isEmpty() || Instant.now().isAfter(expiry)) {
            refreshIssuers();
        }
    }

    private synchronized void refreshIssuers() {
        java.util.LinkedHashSet<String> allow = new java.util.LinkedHashSet<>();
        if (properties.getTrustedIssuers() != null) {
            properties.getTrustedIssuers().stream()
                    .filter(StringUtils::hasText)
                    .forEach(allow::add);
        }
        if (allow.isEmpty()) {
            throw new IllegalStateException("flowledger.iam.trusted-issuers allowlist is empty");
        }
        try {
            List<IamDtos.IssuerInfo> issuers = iamClient.issuers();
            for (IamDtos.IssuerInfo info : issuers) {
                if (info != null && allow.contains(info.issuer()) && StringUtils.hasText(info.jwksUri())) {
                    jwksByUri.put(info.jwksUri(), new CachedJwks(info.jwksUri(), Instant.now(), null));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to refresh IAM /auth/issuers (using allowlist only): {}", e.getMessage());
        }
        // Allowlist is authoritative — never trust issuers outside config even if IAM returns them.
        trustedIssuers = java.util.Set.copyOf(allow);
        issuersLoadedAt = Instant.now();
    }

    private String resolveJwksUri(String issuer) {
        try {
            List<IamDtos.IssuerInfo> issuers = iamClient.issuers();
            for (IamDtos.IssuerInfo info : issuers) {
                if (issuer.equals(info.issuer()) && StringUtils.hasText(info.jwksUri())) {
                    return info.jwksUri();
                }
            }
        } catch (Exception ignored) {
            // fall through
        }
        String normalized = issuer.endsWith("/") ? issuer.substring(0, issuer.length() - 1) : issuer;
        return normalized + "/protocol/openid-connect/certs";
    }

    private JWKSource<SecurityContext> jwksSource(String jwksUri, DefaultResourceRetriever retriever) throws Exception {
        CachedJwks cached = jwksByUri.get(jwksUri);
        Instant now = Instant.now();
        if (cached != null
                && cached.source() != null
                && cached.loadedAt().plusSeconds(properties.getJwksCacheTtlSeconds()).isAfter(now)) {
            return cached.source();
        }
        JWKSource<SecurityContext> source = new RemoteJWKSet<>(new URL(jwksUri), retriever);
        jwksByUri.put(jwksUri, new CachedJwks(jwksUri, now, source));
        return source;
    }

    private record CachedJwks(String uri, Instant loadedAt, JWKSource<SecurityContext> source) {}
}
