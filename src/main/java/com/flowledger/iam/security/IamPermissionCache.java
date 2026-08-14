package com.flowledger.iam.security;

import com.flowledger.common.tenant.TenantContext;
import com.flowledger.iam.client.IamClient;
import com.flowledger.iam.client.IamDtos;
import com.flowledger.iam.config.IamProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Cached AuthorizationContext keyed by {@code authz:{user}:{org}:{membership}}.
 * Never converted to Spring authorities unless identities match immutable TenantContext and statuses are ACTIVE.
 */
@Component
public class IamPermissionCache {
    private final IamProperties properties;
    private final IamClient iamClient;
    private final IamAuthorityMapper authorityMapper;
    private final Cache<String, AuthorizationContext> cache;
    private final long ttlSeconds;

    public IamPermissionCache(IamProperties properties, IamClient iamClient, IamAuthorityMapper authorityMapper) {
        this.properties = properties;
        this.iamClient = iamClient;
        this.authorityMapper = authorityMapper;
        this.ttlSeconds = Math.max(30, properties.getPermissionCacheTtlSeconds());
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(ttlSeconds))
                .maximumSize(10_000)
                .build();
    }

    public void put(AuthorizationContext context) {
        if (context == null
                || context.iamUserId() == null
                || context.iamOrganizationId() == null
                || context.iamMembershipId() == null) {
            return;
        }
        cache.put(key(context.iamUserId(), context.iamOrganizationId(), context.iamMembershipId()), context);
    }

    /**
     * Legacy dual-key put — no-op. Authorization must use the membership triple.
     */
    public void put(UUID iamUserId, UUID iamOrgId, Collection<String> roles, Collection<String> permissions) {
        // intentionally no-op
    }

    public AuthorizationContext get(UUID iamUserId, UUID iamOrgId, UUID iamMembershipId) {
        if (iamUserId == null || iamOrgId == null || iamMembershipId == null) return null;
        AuthorizationContext ctx = cache.getIfPresent(key(iamUserId, iamOrgId, iamMembershipId));
        if (ctx == null || isExpired(ctx)) return null;
        return ctx;
    }

    public AuthorizationContext getOrFetchEffective(
            String accessToken, UUID iamUserId, UUID iamOrgId, UUID iamMembershipId) {
        AuthorizationContext cached = get(iamUserId, iamOrgId, iamMembershipId);
        if (cached != null) return cached;
        if (!StringUtils.hasText(accessToken) || iamOrgId == null) return null;
        try {
            IamDtos.EffectiveAuthorization effective = iamClient.effective(accessToken, iamOrgId);
            if (effective == null) return null;
            if (iamUserId != null
                    && effective.userId() != null
                    && !iamUserId.equals(effective.userId())) {
                return null;
            }
            if (iamMembershipId != null
                    && effective.membershipId() != null
                    && !iamMembershipId.equals(effective.membershipId())) {
                return null;
            }
            AuthorizationContext ctx = fromEffective(effective);
            if (ctx.iamMembershipId() == null && iamMembershipId != null) {
                ctx = new AuthorizationContext(
                        ctx.iamUserId() != null ? ctx.iamUserId() : iamUserId,
                        ctx.iamOrganizationId() != null ? ctx.iamOrganizationId() : iamOrgId,
                        iamMembershipId,
                        ctx.organizationStatus(),
                        ctx.membershipStatus(),
                        ctx.userStatus(),
                        ctx.roles(),
                        ctx.permissions(),
                        ctx.productAccess(),
                        ctx.issuedAt(),
                        ctx.expiresAt());
            }
            if (ctx.iamUserId() == null || ctx.iamOrganizationId() == null || ctx.iamMembershipId() == null) {
                return null;
            }
            put(ctx);
            return ctx;
        } catch (Exception e) {
            return null;
        }
    }

    public AuthorizationContext fromEffective(IamDtos.EffectiveAuthorization effective) {
        if (effective == null) return null;
        Instant now = Instant.now();
        return new AuthorizationContext(
                effective.userId(),
                effective.organizationId(),
                effective.membershipId(),
                effective.organizationStatus(),
                effective.membershipStatus(),
                effective.userStatus(),
                copy(effective.roles()),
                copy(effective.permissions()),
                copy(effective.productAccess()),
                now,
                now.plusSeconds(ttlSeconds));
    }

    /**
     * Converts cache → Spring authorities only when the cached triple matches TenantContext and all statuses are ACTIVE.
     */
    public Collection<String> authoritiesMatching(AuthorizationContext ctx) {
        if (ctx == null) return List.of();
        if (isExpired(ctx)) return List.of();
        if (!isActive(ctx.userStatus())
                || !isActive(ctx.organizationStatus())
                || !isActive(ctx.membershipStatus())) {
            return List.of();
        }
        UUID tenantUser = TenantContext.iamUserId().orElse(null);
        UUID tenantOrg = TenantContext.iamOrganizationId().orElse(null);
        UUID tenantMembership = TenantContext.iamMembershipId().orElse(null);
        if (tenantUser == null || tenantOrg == null || tenantMembership == null) return List.of();
        if (!tenantUser.equals(ctx.iamUserId())
                || !tenantOrg.equals(ctx.iamOrganizationId())
                || !tenantMembership.equals(ctx.iamMembershipId())) {
            return List.of();
        }
        return authorityMapper.toAuthorities(ctx.roles(), ctx.permissions());
    }

    public void evict(UUID iamUserId, UUID iamOrgId, UUID iamMembershipId) {
        if (iamUserId == null || iamOrgId == null || iamMembershipId == null) return;
        cache.invalidate(key(iamUserId, iamOrgId, iamMembershipId));
    }

    private static String key(UUID iamUserId, UUID iamOrgId, UUID iamMembershipId) {
        return "authz:" + iamUserId + ":" + iamOrgId + ":" + iamMembershipId;
    }

    private static boolean isExpired(AuthorizationContext ctx) {
        return ctx.expiresAt() != null && ctx.expiresAt().isBefore(Instant.now());
    }

    private static boolean isActive(String status) {
        return status != null && "ACTIVE".equalsIgnoreCase(status.trim());
    }

    private static List<String> copy(Collection<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    public record AuthorizationContext(
            UUID iamUserId,
            UUID iamOrganizationId,
            UUID iamMembershipId,
            String organizationStatus,
            String membershipStatus,
            String userStatus,
            List<String> roles,
            List<String> permissions,
            List<String> productAccess,
            Instant issuedAt,
            Instant expiresAt) {}
}
