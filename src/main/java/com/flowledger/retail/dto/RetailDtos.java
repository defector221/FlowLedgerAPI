package com.flowledger.retail.dto;

import com.flowledger.retail.domain.RetailEnums.CountType;
import com.flowledger.retail.domain.RetailEnums.GiftCardStatus;
import com.flowledger.retail.domain.RetailEnums.LocationType;
import com.flowledger.retail.domain.RetailEnums.PaymentMode;
import com.flowledger.retail.domain.RetailEnums.PosSaleStatus;
import com.flowledger.retail.domain.RetailEnums.PriceType;
import com.flowledger.retail.domain.RetailEnums.PromoType;
import com.flowledger.retail.domain.RetailEnums.RefundMode;
import com.flowledger.retail.domain.RetailEnums.ReturnReason;
import com.flowledger.retail.domain.RetailEnums.ShiftStatus;
import com.flowledger.retail.domain.RetailEnums.SyncStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class RetailDtos {
    private RetailDtos() {}

    // ---------------------------------------------------------------- Stores
    public record StoreTypeRequest(@NotBlank String code, @NotBlank String name) {}

    public record StoreTypeResponse(UUID id, String code, String name, Long version) {}

    public record StoreRequest(
            @NotBlank String code,
            @NotBlank String name,
            UUID storeTypeId,
            @NotNull UUID warehouseId,
            String address,
            String city,
            String state,
            String phone,
            String status) {}

    public record StoreResponse(
            UUID id,
            String code,
            String name,
            UUID storeTypeId,
            UUID warehouseId,
            String address,
            String city,
            String state,
            String phone,
            String status,
            Long version) {}

    public record CounterRequest(@NotNull UUID storeId, @NotBlank String code, @NotBlank String name, String status) {}

    public record CounterResponse(UUID id, UUID storeId, String code, String name, String status, Long version) {}

    public record TerminalRequest(
            @NotNull UUID storeId, UUID counterId, @NotBlank String code, @NotBlank String name, String status) {}

    public record TerminalResponse(
            UUID id, UUID storeId, UUID counterId, String code, String name, String status, Long version) {}

    public record CashierRequest(
            @NotNull UUID storeId,
            @NotNull UUID userId,
            String employeeCode,
            @NotBlank String displayName,
            String status) {}

    public record CashierResponse(
            UUID id, UUID storeId, UUID userId, String employeeCode, String displayName, String status, Long version) {}

    // ---------------------------------------------------------------- Shifts
    public record OpenShiftRequest(
            @NotNull UUID storeId,
            @NotNull UUID counterId,
            UUID terminalId,
            @NotNull UUID cashierId,
            BigDecimal openingFloat,
            String notes) {}

    public record CloseShiftRequest(@NotNull BigDecimal closingCash, String notes) {}

    public record ShiftResponse(
            UUID id,
            UUID storeId,
            UUID counterId,
            UUID terminalId,
            UUID cashierId,
            ShiftStatus status,
            OffsetDateTime openedAt,
            OffsetDateTime closedAt,
            BigDecimal openingFloat,
            BigDecimal closingCash,
            BigDecimal expectedCash,
            BigDecimal variance,
            String notes,
            Long version) {}

    // ------------------------------------------------------------------- POS
    public record PosSaleRequest(
            @NotNull UUID storeId,
            UUID counterId,
            UUID terminalId,
            UUID shiftId,
            UUID cashierId,
            UUID customerId,
            String notes) {}

    public record PosLineRequest(
            @NotNull UUID productId,
            UUID variantId,
            String description,
            String barcode,
            @NotNull @Positive BigDecimal quantity,
            @NotNull BigDecimal rate,
            BigDecimal discountPercent,
            BigDecimal taxRate) {}

    public record PosLineUpdateRequest(
            @Positive BigDecimal quantity, BigDecimal discountPercent, BigDecimal rate, BigDecimal taxRate) {}

    public record PosAdjustmentsRequest(
            UUID customerId,
            Boolean clearCustomer,
            BigDecimal billDiscountPercent,
            BigDecimal billDiscountAmount,
            BigDecimal loyaltyPointsRedeemed,
            String couponCode) {}

    public record HoldRequest(String heldLabel) {}

    public record PaymentInput(@NotNull PaymentMode paymentMode, @NotNull BigDecimal amount, String reference) {}

    public record CheckoutRequest(UUID customerId, String receiptType, @NotNull List<@Valid PaymentInput> payments) {}

    public record PosLineResponse(
            UUID id,
            UUID productId,
            UUID variantId,
            String description,
            String barcode,
            BigDecimal quantity,
            BigDecimal rate,
            BigDecimal discountPercent,
            BigDecimal taxRate,
            BigDecimal lineTotal,
            int lineOrder) {}

    public record PosPaymentResponse(
            UUID id, PaymentMode paymentMode, BigDecimal amount, UUID paymentId, String reference) {}

    public record PosSaleResponse(
            UUID id,
            UUID storeId,
            UUID counterId,
            UUID terminalId,
            UUID shiftId,
            UUID cashierId,
            UUID customerId,
            UUID salesInvoiceId,
            PosSaleStatus status,
            String receiptType,
            String billNumber,
            BigDecimal subtotal,
            BigDecimal discountTotal,
            BigDecimal billDiscountPercent,
            BigDecimal billDiscountAmount,
            BigDecimal loyaltyPointsRedeemed,
            String couponCode,
            BigDecimal taxTotal,
            BigDecimal grandTotal,
            String heldLabel,
            String notes,
            OffsetDateTime completedAt,
            List<PosLineResponse> lines,
            List<PosPaymentResponse> payments,
            Long version) {}

    // --------------------------------------------------------------- Catalog
    public record BrandRequest(@NotBlank String code, @NotBlank String name) {}

    public record BrandResponse(UUID id, String code, String name, Long version) {}

    public record DepartmentRequest(@NotBlank String code, @NotBlank String name) {}

    public record DepartmentResponse(UUID id, String code, String name, Long version) {}

    public record CollectionRequest(@NotBlank String code, @NotBlank String name, String season) {}

    public record CollectionResponse(UUID id, String code, String name, String season, Long version) {}

    public record VariantRequest(
            @NotNull UUID parentProductId,
            String sku,
            String barcode,
            String color,
            String size,
            String weight,
            String capacity,
            String pattern,
            String material,
            BigDecimal sellingPrice,
            BigDecimal mrp,
            Boolean active) {}

    public record VariantResponse(
            UUID id,
            UUID parentProductId,
            String sku,
            String barcode,
            String color,
            String size,
            String weight,
            String capacity,
            String pattern,
            String material,
            BigDecimal sellingPrice,
            BigDecimal mrp,
            boolean active,
            Long version) {}

    public record BarcodeRequest(
            UUID productId, UUID variantId, @NotBlank String barcode, String barcodeType, Boolean primary) {}

    public record BarcodeResponse(
            UUID id, UUID productId, UUID variantId, String barcode, String barcodeType, boolean primary) {}

    public record ProductLookupResponse(
            UUID productId,
            UUID variantId,
            String name,
            String barcode,
            BigDecimal sellingPrice,
            BigDecimal mrp,
            String hsnSacCode,
            UUID unitId,
            UUID taxRateId) {}

    // --------------------------------------------------------------- Pricing
    public record PriceListRequest(
            @NotBlank String code, @NotBlank String name, PriceType priceType, String currency, Boolean active) {}

    public record PriceListResponse(
            UUID id, String code, String name, PriceType priceType, String currency, boolean active, Long version) {}

    public record PriceListItemRequest(
            @NotNull UUID productId, UUID variantId, @NotNull BigDecimal unitPrice, BigDecimal minQty) {}

    public record PriceListItemResponse(
            UUID id, UUID priceListId, UUID productId, UUID variantId, BigDecimal unitPrice, BigDecimal minQty) {}

    public record ResolvePriceResponse(UUID productId, UUID variantId, BigDecimal unitPrice, String source) {}

    // ------------------------------------------------------------ Promotions
    public record PromotionRequest(
            @NotBlank String code,
            @NotBlank String name,
            @NotNull PromoType promoType,
            BigDecimal discountPercent,
            BigDecimal discountAmount,
            BigDecimal buyQty,
            BigDecimal getQty,
            BigDecimal minBillAmount,
            String couponCode,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            UUID storeId,
            UUID brandId,
            UUID categoryId,
            UUID productId,
            Boolean active) {}

    public record PromotionResponse(
            UUID id,
            String code,
            String name,
            PromoType promoType,
            BigDecimal discountPercent,
            BigDecimal discountAmount,
            BigDecimal buyQty,
            BigDecimal getQty,
            BigDecimal minBillAmount,
            String couponCode,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            UUID storeId,
            UUID brandId,
            UUID categoryId,
            UUID productId,
            boolean active,
            Long version) {}

    public record ApplyCouponRequest(@NotBlank String couponCode, @NotNull BigDecimal billAmount) {}

    public record ApplyCouponResponse(
            String couponCode, boolean applied, BigDecimal discountAmount, BigDecimal netAmount, String message) {}

    // ---------------------------------------------------------------- Returns
    public record ReturnLineRequest(
            @NotNull UUID productId, @NotNull @Positive BigDecimal quantity, @NotNull BigDecimal rate) {}

    public record ReturnRequest(
            @NotNull UUID storeId,
            UUID originalPosSaleId,
            UUID originalInvoiceId,
            @NotNull ReturnReason reason,
            RefundMode refundMode,
            String notes,
            @NotNull List<@Valid ReturnLineRequest> lines) {}

    public record ReturnLineResponse(
            UUID id, UUID productId, BigDecimal quantity, BigDecimal rate, BigDecimal lineTotal) {}

    public record ReturnResponse(
            UUID id,
            UUID storeId,
            UUID originalPosSaleId,
            UUID originalInvoiceId,
            UUID salesReturnId,
            PosSaleStatus status,
            ReturnReason reason,
            RefundMode refundMode,
            String notes,
            BigDecimal totalAmount,
            List<ReturnLineResponse> lines,
            Long version) {}

    // ---------------------------------------------------------------- Loyalty
    public record TierRequest(
            @NotBlank String code, @NotBlank String name, BigDecimal minPoints, BigDecimal earnRate) {}

    public record TierResponse(
            UUID id, String code, String name, BigDecimal minPoints, BigDecimal earnRate, Long version) {}

    public record LoyaltyAccountRequest(@NotNull UUID customerId, UUID tierId) {}

    public record LoyaltyAccountResponse(
            UUID id, UUID customerId, UUID tierId, BigDecimal pointsBalance, BigDecimal lifetimePoints) {}

    public record EarnRequest(
            @NotNull UUID customerId,
            @NotNull BigDecimal points,
            String referenceType,
            UUID referenceId,
            String notes) {}

    public record RedeemRequest(
            @NotNull UUID customerId,
            @NotNull BigDecimal points,
            String referenceType,
            UUID referenceId,
            String notes) {}

    public record LoyaltyTransactionResponse(
            UUID id, UUID accountId, String txnType, BigDecimal points, String referenceType, UUID referenceId) {}

    // -------------------------------------------------------------- GiftCards
    public record GiftCardIssueRequest(
            @NotBlank String cardNumber, @NotNull BigDecimal initialBalance, UUID customerId, LocalDate expiresAt) {}

    public record GiftCardAmountRequest(@NotNull @Positive BigDecimal amount) {}

    public record GiftCardResponse(
            UUID id,
            String cardNumber,
            GiftCardStatus status,
            BigDecimal initialBalance,
            BigDecimal balance,
            UUID customerId,
            LocalDate expiresAt,
            OffsetDateTime activatedAt,
            Long version) {}

    public record GiftCardBalanceResponse(String cardNumber, GiftCardStatus status, BigDecimal balance) {}

    // ------------------------------------------------------------- Inventory
    public record LocationRequest(
            @NotNull UUID storeId,
            @NotNull UUID warehouseId,
            @NotBlank String code,
            @NotBlank String name,
            LocationType locationType) {}

    public record LocationResponse(
            UUID id,
            UUID storeId,
            UUID warehouseId,
            String code,
            String name,
            LocationType locationType,
            Long version) {}

    public record StockCountLineRequest(
            @NotNull UUID productId, BigDecimal systemQty, @NotNull BigDecimal countedQty) {}

    public record StockCountRequest(
            @NotNull UUID storeId,
            UUID locationId,
            CountType countType,
            String notes,
            @NotNull List<@Valid StockCountLineRequest> lines) {}

    public record StockCountLineResponse(
            UUID id, UUID productId, BigDecimal systemQty, BigDecimal countedQty, BigDecimal varianceQty) {}

    public record StockCountResponse(
            UUID id,
            UUID storeId,
            UUID locationId,
            CountType countType,
            String status,
            OffsetDateTime countedAt,
            String notes,
            List<StockCountLineResponse> lines,
            Long version) {}

    // ----------------------------------------------------------------- Labels
    public record LabelTemplateRequest(
            @NotBlank String code, @NotBlank String name, String labelType, @NotBlank String templateBody) {}

    public record LabelTemplateResponse(
            UUID id, String code, String name, String labelType, String templateBody, Long version) {}

    public record RenderLabelRequest(@NotNull UUID templateId, java.util.Map<String, String> values) {}

    public record RenderLabelResponse(UUID templateId, String rendered) {}

    // -------------------------------------------------------------- Analytics
    public record DailySalesResponse(
            LocalDate date,
            long saleCount,
            BigDecimal subtotal,
            BigDecimal discountTotal,
            BigDecimal taxTotal,
            BigDecimal grandTotal) {}

    // ------------------------------------------------------------------- Sync
    public record SyncRequest(
            @NotBlank String clientId, @NotBlank String clientTxnId, UUID storeId, @NotBlank String payloadJson) {}

    public record SyncResponse(UUID id, SyncStatus status, String message) {}
}
