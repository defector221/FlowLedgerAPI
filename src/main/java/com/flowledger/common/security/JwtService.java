package com.flowledger.common.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

@Service
public class JwtService {
    private final JwtProperties properties; private final SecretKey key;
    public JwtService(JwtProperties properties) { this.properties=properties; this.key=Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8)); }
    public String createAccessToken(UserPrincipal p) { return create(p, Duration.ofMinutes(properties.getAccessTokenExpiryMinutes()), "access"); }
    public String createRefreshToken(UserPrincipal p) { return create(p, Duration.ofDays(properties.getRefreshTokenExpiryDays()), "refresh"); }
    private String create(UserPrincipal p, Duration duration, String type) {
        Instant now=Instant.now(); return Jwts.builder().subject(p.getId().toString()).claim("orgId", p.getOrgId()).claim("type", type)
            .issuedAt(Date.from(now)).expiration(Date.from(now.plus(duration))).signWith(key).compact();
    }
    public Claims parse(String token) { return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload(); }
    public boolean isValid(String token, String type) { try { return type.equals(parse(token).get("type",String.class)); } catch (JwtException|IllegalArgumentException e) { return false; } }
    public UUID userId(String token) { return UUID.fromString(parse(token).getSubject()); }
    public UUID organizationId(String token) { Object value=parse(token).get("orgId"); return value == null ? null : UUID.fromString(value.toString()); }
    public long accessExpirySeconds() { return properties.getAccessTokenExpiryMinutes()*60; }
}
