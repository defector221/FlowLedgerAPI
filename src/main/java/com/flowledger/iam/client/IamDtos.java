package com.flowledger.iam.client;

import java.util.List;
import java.util.UUID;

public final class IamDtos {
    private IamDtos() {}

    public record AuthResolveResult(
            String issuer,
            String authorizationEndpoint,
            String tokenEndpoint,
            String jwksUri,
            String endSessionEndpoint,
            String realm,
            String clientId,
            Boolean pkceRequired,
            UUID organizationId) {}

    public record LoginUrlResult(
            String loginUrl, String issuer, String realm, String clientId, String codeChallengeMethod) {}

    public record LogoutUrlResponse(String logoutUrl) {}

    public record IssuerInfo(String issuer, String jwksUri, String realm) {}

    public record MeResult(
            UUID userId,
            String keycloakUserId,
            String email,
            String displayName,
            UUID organizationId,
            String organizationName,
            UUID membershipId,
            String subscriptionPlan,
            List<String> roles,
            List<String> permissions,
            List<String> productAccess,
            List<String> featureFlags,
            String onboardingStatus) {}

    public record EffectiveAuthorization(
            String contextType,
            UUID userId,
            UUID organizationId,
            UUID membershipId,
            String productCode,
            String userStatus,
            String organizationStatus,
            String membershipStatus,
            String subscriptionPlan,
            List<String> roles,
            List<String> permissions,
            List<String> productAccess,
            List<String> featureFlags) {}

    public record MembershipView(
            UUID membershipId,
            UUID userId,
            String email,
            String displayName,
            UUID organizationId,
            String organizationName,
            String organizationSlug,
            String status,
            List<String> roles,
            String invitedAt,
            UUID invitedBy,
            String joinedAt,
            String lastActiveAt) {}

    public record ProvisionRequest(UUID organizationId, String orgSlug) {}

    public record SignupResult(String signupId, String status, String message) {}

    public record AvailabilityResult(
            String type,
            String value,
            Boolean available,
            Boolean definite,
            String checkedVia,
            Boolean bloomMaybeTaken) {}

    public record AvailabilityProbeResult(String type, String value, Boolean maybeTaken) {}

    public record TokenResponse(
            String accessToken, String refreshToken, String idToken, String tokenType, Long expiresIn, String scope) {}
}
