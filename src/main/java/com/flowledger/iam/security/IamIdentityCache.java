package com.flowledger.iam.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Maps Keycloak JWT {@code sub} → local/IAM shell ids so request auth does not depend on a Keycloak DB column
 * and does not call IAM on every request after the first resolution.
 */
@Component
public class IamIdentityCache {
    private final Cache<String, Identity> bySubject;

    public IamIdentityCache() {
        this.bySubject = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofHours(12))
                .maximumSize(50_000)
                .build();
    }

    public void put(String keycloakSubject, Identity identity) {
        if (keycloakSubject == null || identity == null) return;
        bySubject.put(keycloakSubject, identity);
    }

    public Identity get(String keycloakSubject) {
        if (keycloakSubject == null) return null;
        return bySubject.getIfPresent(keycloakSubject);
    }

    public record Identity(
            UUID localUserId,
            UUID localOrgId,
            UUID iamUserId,
            UUID iamOrgId,
            String email,
            boolean platform) {}
}
