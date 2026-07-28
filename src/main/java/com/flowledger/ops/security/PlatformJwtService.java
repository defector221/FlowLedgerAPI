package com.flowledger.ops.security;

import com.flowledger.common.security.JwtProperties;
import com.flowledger.ops.config.OpsProperties;
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
public class PlatformJwtService {
    public static final String TYP_PLATFORM = "platform";

    private final OpsProperties opsProperties;
    private final JwtProperties tenantJwtProperties;
    private SecretKey key;

    public PlatformJwtService(OpsProperties opsProperties, JwtProperties tenantJwtProperties) {
        this.opsProperties = opsProperties;
        this.tenantJwtProperties = tenantJwtProperties;
    }

    private SecretKey key() {
        if (key == null) {
            String secret = opsProperties.getJwt().getSecret();
            if (secret == null || secret.isBlank()) {
                secret = tenantJwtProperties.getSecret() + "|platform-ops";
            }
            if (secret.length() < 32) {
                secret = (secret + "FlowLedgerPlatformOpsSecretPad!!").substring(0, 48);
            }
            key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        }
        return key;
    }

    public String createAccessToken(PlatformPrincipal p) {
        return create(p, Duration.ofMinutes(opsProperties.getJwt().getAccessTokenExpiryMinutes()), "access");
    }

    public String createRefreshToken(PlatformPrincipal p) {
        return create(p, Duration.ofDays(opsProperties.getJwt().getRefreshTokenExpiryDays()), "refresh");
    }

    private String create(PlatformPrincipal p, Duration duration, String type) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(p.getId().toString())
                .claim("typ", TYP_PLATFORM)
                .claim("type", type)
                .claim("email", p.getEmail())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(duration)))
                .signWith(key())
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key()).build().parseSignedClaims(token).getPayload();
    }

    public boolean isValidPlatformAccess(String token) {
        try {
            Claims c = parse(token);
            return TYP_PLATFORM.equals(c.get("typ", String.class)) && "access".equals(c.get("type", String.class));
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public boolean isValidPlatformRefresh(String token) {
        try {
            Claims c = parse(token);
            return TYP_PLATFORM.equals(c.get("typ", String.class)) && "refresh".equals(c.get("type", String.class));
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public UUID userId(String token) {
        return UUID.fromString(parse(token).getSubject());
    }
}
