package com.flowledger.iam.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flowledger.common.tenant.TenantContext;
import com.flowledger.iam.client.IamClient;
import com.flowledger.iam.client.IamDtos;
import com.flowledger.iam.config.IamProperties;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

/**
 * Fail-closed AuthorizationContext cache: incomplete triples are never stored;
 * authorities require TenantContext identity match + ACTIVE statuses.
 * Put B → evict A keeps target readable (atomic switch cache sequence).
 */
class IamPermissionCacheTest {
    private IamPermissionCache cache;
    private UUID userId;
    private UUID orgId;
    private UUID membershipId;

    @BeforeEach
    void setUp() {
        IamProperties properties = new IamProperties();
        properties.setPermissionCacheTtlSeconds(120);
        cache = new IamPermissionCache(properties, new IamClient(properties, new RestTemplate()), new IamAuthorityMapper());
        userId = UUID.randomUUID();
        orgId = UUID.randomUUID();
        membershipId = UUID.randomUUID();
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void putRejectsIncompleteTriple_failClosed() {
        IamPermissionCache.AuthorizationContext incomplete = new IamPermissionCache.AuthorizationContext(
                userId,
                orgId,
                null,
                "ACTIVE",
                "ACTIVE",
                "ACTIVE",
                List.of("ORG_ADMIN"),
                List.of("invoice:read"),
                List.of(),
                null,
                null);
        cache.put(incomplete);
        assertNull(cache.get(userId, orgId, membershipId));
    }

    @Test
    void putThenGetUsesMembershipTripleKey() {
        IamPermissionCache.AuthorizationContext ctx = fromStatuses("ACTIVE", "ACTIVE", "ACTIVE");
        cache.put(ctx);
        assertEquals(membershipId, cache.get(userId, orgId, membershipId).iamMembershipId());
        assertNull(cache.get(userId, orgId, UUID.randomUUID()));
    }

    @Test
    void atomicSwitchSequence_putBThenEvictAKeepsTargetReadable() {
        UUID orgA = UUID.randomUUID();
        UUID memA = UUID.randomUUID();

        IamPermissionCache.AuthorizationContext authzA = new IamPermissionCache.AuthorizationContext(
                userId,
                orgA,
                memA,
                "ACTIVE",
                "ACTIVE",
                "ACTIVE",
                List.of("VIEWER"),
                List.of(),
                List.of(),
                null,
                null);
        cache.put(authzA);
        cache.put(fromStatuses("ACTIVE", "ACTIVE", "ACTIVE"));
        cache.evict(userId, orgA, memA);

        assertNull(cache.get(userId, orgA, memA));
        assertEquals(membershipId, cache.get(userId, orgId, membershipId).iamMembershipId());
    }

    @Test
    void authoritiesMatchingFailsClosedWhenTenantContextMismatch() {
        cache.put(fromStatuses("ACTIVE", "ACTIVE", "ACTIVE"));
        TenantContext.bindImmutable(UUID.randomUUID(), UUID.randomUUID(), userId, orgId, UUID.randomUUID());
        assertTrue(cache.authoritiesMatching(cache.get(userId, orgId, membershipId)).isEmpty());
    }

    @Test
    void authoritiesMatchingFailsClosedWhenNotActive() {
        IamPermissionCache.AuthorizationContext suspended = fromStatuses("ACTIVE", "SUSPENDED", "ACTIVE");
        cache.put(suspended);
        TenantContext.bindImmutable(UUID.randomUUID(), UUID.randomUUID(), userId, orgId, membershipId);
        assertTrue(cache.authoritiesMatching(cache.get(userId, orgId, membershipId)).isEmpty());
    }

    @Test
    void fromEffectiveMapsMembershipTriple() {
        IamDtos.EffectiveAuthorization effective = new IamDtos.EffectiveAuthorization(
                "ORGANIZATION",
                userId,
                orgId,
                membershipId,
                "flowledger",
                "ACTIVE",
                "ACTIVE",
                "ACTIVE",
                "standard",
                List.of("ORG_ADMIN"),
                List.of("invoice:read"),
                List.of("flowledger"),
                List.of());
        IamPermissionCache.AuthorizationContext ctx = cache.fromEffective(effective);
        assertEquals(userId, ctx.iamUserId());
        assertEquals(orgId, ctx.iamOrganizationId());
        assertEquals(membershipId, ctx.iamMembershipId());
        assertEquals(List.of("ORG_ADMIN"), ctx.roles());
    }

    private IamPermissionCache.AuthorizationContext fromStatuses(String org, String membership, String user) {
        return new IamPermissionCache.AuthorizationContext(
                userId,
                orgId,
                membershipId,
                org,
                membership,
                user,
                List.of("ORG_ADMIN"),
                List.of("invoice:read"),
                List.of(),
                null,
                null);
    }
}
