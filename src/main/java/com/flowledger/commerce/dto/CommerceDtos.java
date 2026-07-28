package com.flowledger.commerce.dto;

import com.flowledger.commerce.customer.domain.AddressType;
import com.flowledger.commerce.integration.domain.ConnectorType;
import com.flowledger.commerce.integration.domain.IntegrationType;
import com.flowledger.commerce.merchant.domain.MerchantType;
import com.flowledger.commerce.onboarding.domain.MerchantOnboardingState;
import com.flowledger.commerce.store.domain.MarketplaceVisibility;
import com.flowledger.commerce.store.domain.StoreCommerceStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
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
            @NotBlank String mobile, String email, String firstName, String lastName, UUID organizationId, String referralCode) {}

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

    public record StoreCommerceUpdateResponse(StoreCommerceResponse profile, UUID syncJobId) {}

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

    public record PublishResultResponse(
            UUID storeId, Integer productsPublished, Boolean storePublished, UUID jobId, String status) {}

    public record CommerceJobResponse(
            UUID id,
            String jobType,
            String entityType,
            UUID organizationId,
            UUID storeId,
            UUID entityId,
            String status,
            String syncMode,
            String lastError,
            String resultDetail,
            int attempts,
            OffsetDateTime scheduledAt,
            OffsetDateTime completedAt,
            OffsetDateTime createdAt) {}

    public record CapabilityUpdateResponse(CapabilityProfileResponse capabilities, UUID syncJobId) {}

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

    // Cart & Checkout (Epic 3)
    public record CreateCartRequest(@NotNull UUID storeId) {}

    public record AddCartItemRequest(@NotNull UUID productIndexId, java.math.BigDecimal quantity) {}

    public record UpdateCartItemRequest(@NotNull java.math.BigDecimal quantity) {}

    public record CartItemResponse(
            UUID id,
            UUID productId,
            UUID variantId,
            java.math.BigDecimal quantity,
            java.math.BigDecimal lineSubtotal,
            java.math.BigDecimal lineTax,
            java.math.BigDecimal lineTotal,
            String name,
            String sku,
            String barcode,
            java.util.List<String> imageUrls) {}

    public record CartResponse(
            UUID id,
            UUID storeId,
            UUID organizationId,
            String status,
            String currency,
            java.math.BigDecimal subtotal,
            java.math.BigDecimal discountTotal,
            java.math.BigDecimal taxTotal,
            java.math.BigDecimal grandTotal,
            int itemCount,
            java.util.List<CartItemResponse> items) {}

    public record StartCheckoutRequest(
            @NotNull UUID cartId,
            @NotNull com.flowledger.commerce.fulfillment.FulfillmentType fulfillmentType,
            UUID addressId,
            String couponCode) {}

    public record CheckoutSessionResponse(
            UUID id,
            UUID cartId,
            UUID storeId,
            String status,
            String fulfillmentType,
            UUID addressId,
            String couponCode,
            String currency,
            java.math.BigDecimal subtotal,
            java.math.BigDecimal discountTotal,
            java.math.BigDecimal taxTotal,
            java.math.BigDecimal shippingTotal,
            java.math.BigDecimal grandTotal,
            OffsetDateTime expiresAt,
            java.util.List<CartItemResponse> items) {}

    public record ApplyCheckoutCouponRequest(@NotBlank String couponCode) {}

    public record InitiatePaymentRequest(
            @NotNull com.flowledger.commerce.payment.domain.CommercePaymentProvider provider) {}

    public record PaymentSessionResponse(
            UUID id,
            UUID checkoutSessionId,
            String provider,
            String status,
            java.math.BigDecimal amount,
            String currency,
            String gatewayOrderId) {}

    public record ConfirmCheckoutRequest(String gatewayPaymentId, String gatewaySignature) {}

    public record CommerceOrderResponse(
            UUID id,
            String orderNumber,
            UUID storeId,
            String status,
            String fulfillmentType,
            String currency,
            java.math.BigDecimal subtotal,
            java.math.BigDecimal discountTotal,
            java.math.BigDecimal taxTotal,
            java.math.BigDecimal shippingTotal,
            java.math.BigDecimal grandTotal,
            OffsetDateTime placedAt,
            OffsetDateTime confirmedAt,
            java.util.List<CommerceOrderLineResponse> lines) {}

    public record CommerceOrderLineResponse(
            UUID id,
            UUID productId,
            java.math.BigDecimal quantity,
            java.math.BigDecimal lineSubtotal,
            java.math.BigDecimal lineTax,
            java.math.BigDecimal lineTotal,
            String name,
            String sku) {}

    // Fulfillment (Epic 4)
    public record AcceptFulfillmentRequest(@NotNull UUID fulfillmentOrderId) {}

    public record FulfillmentTaskRequest(@NotNull UUID fulfillmentOrderId, UUID staffId) {}

    public record AssignDriverRequest(@NotNull UUID fulfillmentOrderId, @NotNull UUID driverId) {}

    public record VerifyCollectRequest(@NotBlank String token) {}

    public record FulfillmentOrderResponse(
            UUID id,
            UUID commerceOrderId,
            String orderNumber,
            UUID storeId,
            UUID customerId,
            String fulfillmentType,
            String status,
            String subStatus,
            OffsetDateTime acceptedAt,
            OffsetDateTime readyAt,
            OffsetDateTime completedAt) {}

    public record FulfillmentOrderDetailResponse(
            UUID id,
            UUID commerceOrderId,
            String orderNumber,
            UUID storeId,
            UUID customerId,
            String fulfillmentType,
            String status,
            String subStatus,
            OffsetDateTime acceptedAt,
            OffsetDateTime readyAt,
            OffsetDateTime completedAt,
            String currency,
            java.math.BigDecimal grandTotal,
            UUID erpSalesOrderId,
            String erpSalesOrderNumber,
            UUID erpInvoiceId,
            String erpInvoiceNumber,
            java.util.List<FulfillmentOrderLineResponse> lines) {}

    public record FulfillmentOrderLineResponse(
            UUID id,
            UUID productId,
            java.math.BigDecimal quantity,
            java.math.BigDecimal returnableQty,
            java.math.BigDecimal lineSubtotal,
            java.math.BigDecimal lineTax,
            java.math.BigDecimal lineTotal,
            String name,
            String sku) {}

    public record CreateCommerceReturnRequest(
            LocalDate returnDate,
            String notes,
            @NotEmpty @Valid java.util.List<CommerceReturnLineRequest> lines) {}

    public record CommerceReturnLineRequest(
            @NotNull UUID orderLineId, @NotNull @Positive java.math.BigDecimal quantity) {}

    public record CommerceOrderReturnResponse(
            UUID id,
            UUID commerceOrderId,
            UUID fulfillmentOrderId,
            UUID erpSalesReturnId,
            String returnNumber,
            String status,
            java.util.List<CommerceReturnLineResponse> lines,
            OffsetDateTime createdAt) {}

    public record CommerceReturnLineResponse(
            UUID orderLineId,
            UUID productId,
            java.math.BigDecimal quantity,
            java.math.BigDecimal rate,
            java.math.BigDecimal lineTotal) {}

    public record FulfillmentDashboardResponse(
            long pendingAcceptance,
            long picking,
            long packing,
            long ready,
            long delivery,
            long pickup,
            long completed) {}

    public record TrackingMilestoneResponse(String label, boolean reached, OffsetDateTime reachedAt) {}

    public record OrderTrackingResponse(
            UUID orderId,
            UUID fulfillmentOrderId,
            String status,
            String subStatus,
            java.util.List<TrackingMilestoneResponse> timeline) {}

    public record PickupQrResponse(UUID orderId, UUID fulfillmentOrderId, String collectCode) {}

    public record FulfillmentSlotResponse(
            UUID id,
            UUID storeId,
            String slotType,
            java.time.LocalDate slotDate,
            String startTime,
            String endTime,
            int capacity,
            int booked) {}

    public record BookSlotRequest(@NotNull UUID slotId, @NotNull UUID checkoutSessionId) {}

    public record ScanSessionResponse(
            UUID id,
            UUID storeId,
            String status,
            String currency,
            java.math.BigDecimal subtotal,
            java.math.BigDecimal taxTotal,
            java.math.BigDecimal grandTotal,
            int itemCount,
            java.util.List<ScanSessionItemResponse> items) {}

    public record ScanSessionItemResponse(
            UUID id,
            UUID productId,
            java.math.BigDecimal quantity,
            java.math.BigDecimal lineTotal,
            String name,
            String barcode) {}

    public record OpenScanSessionRequest(@NotNull UUID storeId) {}

    public record ScanItemRequest(@NotBlank String barcode, java.math.BigDecimal quantity) {}

    public record ScanExitResponse(UUID sessionId, String exitToken) {}
}
