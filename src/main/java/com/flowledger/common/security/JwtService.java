package com.flowledger.common.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final JwtProperties properties;
    private final SecretKey key;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(UserPrincipal p) {
        return create(p, Duration.ofMinutes(properties.getAccessTokenExpiryMinutes()), "access");
    }

    public String createRefreshToken(UserPrincipal p) {
        return create(p, Duration.ofDays(properties.getRefreshTokenExpiryDays()), "refresh");
    }

    public String createAccessToken(UserPrincipal p, UUID branchId, UUID storeId, UUID warehouseId) {
        return createWithLocation(
                p,
                Duration.ofMinutes(properties.getAccessTokenExpiryMinutes()),
                "access",
                branchId,
                storeId,
                warehouseId);
    }

    private String create(UserPrincipal p, Duration duration, String type) {
        return createWithLocation(p, duration, type, p.getBranchId(), p.getStoreId(), p.getWarehouseId());
    }

    private String createWithLocation(
            UserPrincipal p, Duration duration, String type, UUID branchId, UUID storeId, UUID warehouseId) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(p.getId().toString())
                .claim("orgId", p.getOrgId())
                .claim("type", type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(duration)));
        if ("refresh".equals(type)) {
            builder.id(UUID.randomUUID().toString());
        }
        if (branchId != null) builder.claim("branchId", branchId.toString());
        if (storeId != null) builder.claim("storeId", storeId.toString());
        if (warehouseId != null) builder.claim("warehouseId", warehouseId.toString());
        return builder.signWith(key).compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public boolean isValid(String token, String type) {
        try {
            return type.equals(parse(token).get("type", String.class));
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public UUID userId(String token) {
        return UUID.fromString(parse(token).getSubject());
    }

    public UUID organizationId(String token) {
        Object value = parse(token).get("orgId");
        return value == null ? null : UUID.fromString(value.toString());
    }

    public UUID branchId(String token) {
        return uuidClaim(parse(token).get("branchId"));
    }

    public UUID storeId(String token) {
        return uuidClaim(parse(token).get("storeId"));
    }

    public UUID warehouseId(String token) {
        return uuidClaim(parse(token).get("warehouseId"));
    }

    private static UUID uuidClaim(Object value) {
        return value == null ? null : UUID.fromString(value.toString());
    }

    public long accessExpirySeconds() {
        return properties.getAccessTokenExpiryMinutes() * 60;
    }
}
