package com.flowledger.commerce.auth;

import com.flowledger.commerce.config.CommerceProperties;
import com.flowledger.common.security.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class CommerceJwtService {
    public static final String TYP_COMMERCE = "commerce";

    private final CommerceProperties commerceProperties;
    private final JwtProperties tenantJwtProperties;
    private SecretKey key;

    public CommerceJwtService(CommerceProperties commerceProperties, JwtProperties tenantJwtProperties) {
        this.commerceProperties = commerceProperties;
        this.tenantJwtProperties = tenantJwtProperties;
    }

    private SecretKey key() {
        if (key == null) {
            String secret = commerceProperties.getJwt().getSecret();
            if (secret == null || secret.isBlank()) {
                secret = tenantJwtProperties.getSecret() + "|commerce";
            }
            if (secret.length() < 32) {
                secret = (secret + "FlowLedgerCommerceSecretPad!!").substring(0, 48);
            }
            key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        }
        return key;
    }

    public String createAccessToken(CommercePrincipal principal) {
        return create(principal, Duration.ofMinutes(commerceProperties.getJwt().getAccessTokenExpiryMinutes()), "access");
    }

    public String createRefreshToken(CommercePrincipal principal) {
        return create(principal, Duration.ofDays(commerceProperties.getJwt().getRefreshTokenExpiryDays()), "refresh");
    }

    private String create(CommercePrincipal principal, Duration duration, String type) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(principal.getCustomerId().toString())
                .claim("typ", TYP_COMMERCE)
                .claim("type", type)
                .claim("mobile", principal.getMobile())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(duration)));
        if ("refresh".equals(type)) {
            builder.id(UUID.randomUUID().toString());
        }
        return builder.signWith(key()).compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key()).build().parseSignedClaims(token).getPayload();
    }

    public boolean isValidAccess(String token) {
        try {
            Claims c = parse(token);
            return TYP_COMMERCE.equals(c.get("typ", String.class)) && "access".equals(c.get("type", String.class));
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public boolean isValidRefresh(String token) {
        try {
            Claims c = parse(token);
            return TYP_COMMERCE.equals(c.get("typ", String.class)) && "refresh".equals(c.get("type", String.class));
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public UUID customerId(String token) {
        return UUID.fromString(parse(token).getSubject());
    }
}
