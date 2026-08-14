package com.flowledger.iam.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Server-side OAuth token store. Keycloak access/refresh never go to the browser — only an opaque
 * session id in an HttpOnly cookie references entries here.
 */
@Component
public class IamServerSessionStore {
    private final Cache<String, Session> sessions;

    public IamServerSessionStore() {
        this.sessions = Caffeine.newBuilder()
                .expireAfterAccess(Duration.ofDays(14))
                .maximumSize(50_000)
                .build();
    }

    public void put(String sessionId, Session session) {
        sessions.put(sessionId, session);
    }

    public Session get(String sessionId) {
        if (sessionId == null) return null;
        return sessions.getIfPresent(sessionId);
    }

    public void remove(String sessionId) {
        if (sessionId != null) sessions.invalidate(sessionId);
    }

    public record Session(
            boolean ops,
            String accessToken,
            String refreshToken,
            String idToken,
            String tokenEndpoint,
            String clientId,
            String clientSecret,
            UUID iamUserId,
            UUID iamOrganizationId,
            UUID iamMembershipId,
            UUID localUserId,
            UUID localOrgId,
            String email,
            Instant createdAt,
            Instant accessExpiresAt) {
        public Session withTokens(String access, String refresh, String id, Instant accessExpiresAt) {
            return new Session(
                    ops,
                    access,
                    refresh != null ? refresh : refreshToken,
                    id != null ? id : idToken,
                    tokenEndpoint,
                    clientId,
                    clientSecret,
                    iamUserId,
                    iamOrganizationId,
                    iamMembershipId,
                    localUserId,
                    localOrgId,
                    email,
                    createdAt,
                    accessExpiresAt);
        }

        public Session withActiveOrganization(UUID iamOrgId, UUID iamMembershipId, UUID localOrgId) {
            return new Session(
                    ops,
                    accessToken,
                    refreshToken,
                    idToken,
                    tokenEndpoint,
                    clientId,
                    clientSecret,
                    iamUserId,
                    iamOrgId,
                    iamMembershipId,
                    localUserId,
                    localOrgId,
                    email,
                    createdAt,
                    accessExpiresAt);
        }
    }
}
