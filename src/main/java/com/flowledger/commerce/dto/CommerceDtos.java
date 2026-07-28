package com.flowledger.commerce.dto;

import com.flowledger.commerce.customer.domain.AddressType;
import com.flowledger.commerce.integration.domain.ConnectorType;
import com.flowledger.commerce.integration.domain.IntegrationType;
import com.flowledger.commerce.merchant.domain.MerchantType;
import com.flowledger.commerce.onboarding.domain.MerchantOnboardingState;
import com.flowledger.commerce.store.domain.MarketplaceVisibility;
import com.flowledger.commerce.store.domain.StoreCommerceStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class CommerceDtos {
    private CommerceDtos() {}

    // Auth
    public record RequestOtpRequest(@NotBlank String mobile) {}

    public record RequestOtpResponse(String mobile, boolean otpSent, String otp) {}

    public record VerifyOtpRequest(@NotBlank String mobile, @NotBlank String otp) {}

    public record CommerceTokenResponse(
            String accessToken, String refreshToken, UUID customerId, String mobile) {}

    public record RefreshTokenRequest(@NotBlank String refreshToken) {}

    // Customer
    public record RegisterCustomerRequest(
            @NotBlank String mobile, String email, String firstName, String lastName, UUID organizationId) {}

    public record UpdateCustomerRequest(
            String email, String firstName, String lastName, String displayName, String preferredLanguage,
            Boolean marketingConsent, Boolean notificationConsent) {}

    public record CustomerResponse(
            UUID id,
            String mobile,
            String email,
            String firstName,
            String lastName,
            String displayName,
            String status,
            String onboardingState) {}

    public record AddressRequest(
            AddressType addressType,
            String house,
            String street,
            String landmark,
            String city,
            String district,
            String state,
            String country,
            String pincode,
            BigDecimal latitude,
            BigDecimal longitude,
            Boolean defaultAddress) {}

    public record AddressResponse(
            UUID id,
            AddressType addressType,
            String house,
            String street,
            String landmark,
            String city,
            String district,
            String state,
            String country,
            String pincode,
            BigDecimal latitude,
            BigDecimal longitude,
            boolean defaultAddress) {}

    public record MembershipResponse(
            UUID id, UUID customerId, UUID organizationId, UUID favoriteStoreId, String status, OffsetDateTime joinedAt) {}

    public record UpdateMembershipStatusRequest(@NotBlank String status) {}

    // Merchant
    public record MerchantProfileResponse(
            UUID organizationId,
            MerchantOnboardingState onboardingState,
            IntegrationProfileResponse integration,
            CapabilityProfileResponse capabilities) {}

    public record IntegrationProfileResponse(
            MerchantType merchantType,
            IntegrationType integrationType,
            ConnectorType connectorType,
            String status,
            String configurationJson,
            String healthStatus,
            OffsetDateTime lastSyncAt,
            OffsetDateTime lastHealthCheckAt) {}

    public record UpdateIntegrationRequest(
            IntegrationType integrationType, ConnectorType connectorType, String status, String configurationJson) {}

    public record CapabilityProfileResponse(
            boolean supportsMarketplace,
            boolean supportsDelivery,
            boolean supportsPickup,
            boolean supportsClickCollect,
            boolean supportsScanAndGo,
            boolean supportsScheduledDelivery,
            boolean supportsScheduledPickup,
            boolean supportsWallet,
            boolean supportsCoupons,
            boolean supportsLoyalty,
            boolean supportsRecommendations,
            boolean supportsReviews,
            boolean supportsReturns,
            boolean supportsGiftCards) {}

    public record UpdateCapabilitiesRequest(
            Boolean supportsMarketplace,
            Boolean supportsDelivery,
            Boolean supportsPickup,
            Boolean supportsClickCollect,
            Boolean supportsScanAndGo,
            Boolean supportsScheduledDelivery,
            Boolean supportsScheduledPickup,
            Boolean supportsWallet,
            Boolean supportsCoupons,
            Boolean supportsLoyalty,
            Boolean supportsRecommendations,
            Boolean supportsReviews,
            Boolean supportsReturns,
            Boolean supportsGiftCards) {}

    // Store commerce
    public record StoreCommerceResponse(
            UUID id,
            UUID storeId,
            boolean commerceEnabled,
            boolean acceptOnlineOrders,
            boolean supportsDelivery,
            boolean supportsPickup,
            boolean supportsClickCollect,
            boolean supportsScanAndGo,
            boolean publishedToMarketplace,
            boolean publishProducts,
            boolean publishInventory,
            boolean publishPrices,
            MarketplaceVisibility visibility,
            BigDecimal discoveryRadius,
            StoreCommerceStatus status) {}

    public record UpdateStoreCommerceRequest(
            Boolean commerceEnabled,
            Boolean acceptOnlineOrders,
            Boolean supportsDelivery,
            Boolean supportsPickup,
            Boolean supportsClickCollect,
            Boolean supportsScanAndGo,
            Boolean publishedToMarketplace,
            Boolean publishProducts,
            Boolean publishInventory,
            Boolean publishPrices,
            MarketplaceVisibility visibility,
            BigDecimal discoveryRadius,
            StoreCommerceStatus status) {}

    public record PublishResultResponse(UUID storeId, int productsPublished, boolean storePublished) {}

    // Ops
    public record OpsOnboardMerchantRequest(
            @NotNull MerchantType merchantType, ConnectorType connectorType, IntegrationType integrationType) {}

    public record OpsMerchantRow(
            UUID organizationId,
            String organizationName,
            MerchantOnboardingState state,
            MerchantType merchantType,
            String healthStatus,
            OffsetDateTime lastSyncAt) {}

    public record OpsMerchantDetailResponse(
            UUID organizationId,
            String organizationName,
            MerchantOnboardingState onboardingState,
            IntegrationProfileResponse integration,
            CapabilityProfileResponse capabilities,
            long commerceEnabledStores,
            long publishedStores) {}

    public record OpsOnboardingTransitionRequest(@NotNull MerchantOnboardingState state) {}

    public record IntegrationHealthRow(
            UUID organizationId,
            String organizationName,
            MerchantType merchantType,
            ConnectorType connectorType,
            String healthStatus,
            OffsetDateTime lastSyncAt,
            OffsetDateTime lastHealthCheckAt) {}

    public record CommerceDashboardSummary(
            long totalMerchants, long liveMerchants, long suspendedMerchants, long unhealthyIntegrations) {}

    // Marketplace (public read APIs — source-agnostic, no org/merchantType)
    public record MarketplaceStoreResponse(
            UUID id,
            UUID storeId,
            String name,
            String city,
            String postalCode,
            String state,
            String country,
            java.math.BigDecimal latitude,
            java.math.BigDecimal longitude,
            boolean supportsDelivery,
            boolean supportsPickup,
            boolean supportsClickCollect,
            boolean supportsScanAndGo,
            Double distanceKm) {}

    public record MarketplaceProductResponse(
            UUID id,
            UUID storeId,
            UUID productId,
            String sku,
            String barcode,
            String gtin,
            String name,
            String description,
            String brand,
            UUID categoryId,
            String categoryName,
            java.math.BigDecimal price,
            String currency,
            java.math.BigDecimal inventoryQty,
            java.util.List<String> imageUrls) {}

    public record MarketplaceCategoryResponse(UUID id, UUID categoryId, String name, UUID parentId, int productCount) {}

    public record MarketplaceBrandResponse(UUID id, String brandName, int productCount) {}

    public record MarketplaceStoreAvailability(
            UUID storeId,
            String storeName,
            String city,
            Double distanceKm,
            java.math.BigDecimal price,
            java.math.BigDecimal inventoryQty) {}

    public record MarketplaceBarcodeLookupResponse(
            MarketplaceProductResponse product, java.util.List<MarketplaceStoreAvailability> stores) {}
}
