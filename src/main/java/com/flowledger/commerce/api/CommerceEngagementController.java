package com.flowledger.commerce.api;

import com.flowledger.commerce.auth.CommerceSecurityContext;
import com.flowledger.commerce.engagement.CustomerTimelineService;
import com.flowledger.commerce.engagement.EngagementService;
import com.flowledger.commerce.engagement.CommerceCustomerNotificationService;
import com.flowledger.commerce.engagement.entity.CommerceCustomerNotification;
import com.flowledger.commerce.engagement.entity.CommerceReview;
import com.flowledger.commerce.engagement.entity.CommerceSavedList;
import com.flowledger.commerce.engagement.entity.CommerceSavedListItem;
import com.flowledger.commerce.engagement.entity.CommerceWishlistItem;
import com.flowledger.commerce.loyalty.CommerceLoyaltyBridgeService;
import com.flowledger.commerce.referral.ReferralService;
import com.flowledger.commerce.referral.entity.CommerceReferralCode;
import com.flowledger.commerce.wallet.WalletLedgerService;
import com.flowledger.commerce.wallet.entity.CommerceWalletAccount;
import com.flowledger.commerce.wallet.entity.CommerceWalletEntry;
import com.flowledger.common.dto.ApiResponse;
import com.flowledger.retail.dto.RetailDtos.LoyaltyAccountResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/commerce/customers/me")
@PreAuthorize("hasAuthority('COMMERCE_CUSTOMER')")
public class CommerceEngagementController {
    private final WalletLedgerService wallet;
    private final CommerceLoyaltyBridgeService loyalty;
    private final ReferralService referrals;
    private final CommerceCustomerNotificationService notifications;
    private final EngagementService engagement;
    private final CustomerTimelineService timeline;

    public CommerceEngagementController(
            WalletLedgerService wallet,
            CommerceLoyaltyBridgeService loyalty,
            ReferralService referrals,
            CommerceCustomerNotificationService notifications,
            EngagementService engagement,
            CustomerTimelineService timeline) {
        this.wallet = wallet;
        this.loyalty = loyalty;
        this.referrals = referrals;
        this.notifications = notifications;
        this.engagement = engagement;
        this.timeline = timeline;
    }

    @GetMapping("/wallet")
    public ApiResponse<List<CommerceWalletAccount>> wallet(@RequestParam UUID organizationId) {
        UUID customerId = CommerceSecurityContext.currentCustomer().getCustomerId();
        return ApiResponse.of(wallet.listAccounts(customerId, organizationId));
    }

    @GetMapping("/wallet/{accountId}/entries")
    public ApiResponse<List<CommerceWalletEntry>> walletEntries(@PathVariable UUID accountId) {
        return ApiResponse.of(wallet.history(accountId));
    }

    @GetMapping("/loyalty")
    public ApiResponse<LoyaltyAccountResponse> loyalty(@RequestParam UUID organizationId) {
        UUID customerId = CommerceSecurityContext.currentCustomer().getCustomerId();
        return ApiResponse.of(loyalty.getAccount(organizationId, customerId));
    }

    @GetMapping("/referral-code")
    public ApiResponse<CommerceReferralCode> referralCode(@RequestParam UUID organizationId) {
        UUID customerId = CommerceSecurityContext.currentCustomer().getCustomerId();
        return ApiResponse.of(referrals.getOrCreateCode(customerId, organizationId));
    }

    @GetMapping("/notifications")
    public ApiResponse<List<CommerceCustomerNotification>> notifications() {
        UUID customerId = CommerceSecurityContext.currentCustomer().getCustomerId();
        return ApiResponse.of(notifications.list(customerId));
    }

    @PostMapping("/notifications/{id}/read")
    public ApiResponse<Void> markNotificationRead(@PathVariable UUID id) {
        UUID customerId = CommerceSecurityContext.currentCustomer().getCustomerId();
        notifications.markRead(id, customerId);
        return ApiResponse.of(null);
    }

    @GetMapping("/wishlist")
    public ApiResponse<List<CommerceWishlistItem>> wishlist(@RequestParam UUID organizationId) {
        UUID customerId = CommerceSecurityContext.currentCustomer().getCustomerId();
        return ApiResponse.of(engagement.listWishlist(customerId, organizationId));
    }

    @PostMapping("/wishlist")
    public ApiResponse<CommerceWishlistItem> addWishlist(@RequestBody WishlistRequest request) {
        UUID customerId = CommerceSecurityContext.currentCustomer().getCustomerId();
        return ApiResponse.of(engagement.addWishlist(
                customerId, request.organizationId(), request.storeId(), request.productId()));
    }

    @DeleteMapping("/wishlist")
    public ApiResponse<Void> removeWishlist(@RequestParam UUID storeId, @RequestParam UUID productId) {
        UUID customerId = CommerceSecurityContext.currentCustomer().getCustomerId();
        engagement.removeWishlist(customerId, storeId, productId);
        return ApiResponse.of(null);
    }

    @GetMapping("/saved-lists")
    public ApiResponse<List<CommerceSavedList>> savedLists(@RequestParam UUID organizationId) {
        UUID customerId = CommerceSecurityContext.currentCustomer().getCustomerId();
        return ApiResponse.of(engagement.listSavedLists(customerId, organizationId));
    }

    @PostMapping("/saved-lists")
    public ApiResponse<CommerceSavedList> createList(@RequestBody CreateListRequest request) {
        UUID customerId = CommerceSecurityContext.currentCustomer().getCustomerId();
        return ApiResponse.of(engagement.createList(customerId, request.organizationId(), request.name()));
    }

    @PostMapping("/saved-lists/{listId}/items")
    public ApiResponse<CommerceSavedListItem> addListItem(
            @PathVariable UUID listId, @RequestBody ListItemRequest request) {
        UUID customerId = CommerceSecurityContext.currentCustomer().getCustomerId();
        return ApiResponse.of(engagement.addListItem(listId, customerId, request.productId(), request.quantity()));
    }

    @PostMapping("/reviews")
    public ApiResponse<CommerceReview> submitReview(@RequestBody ReviewRequest request) {
        UUID customerId = CommerceSecurityContext.currentCustomer().getCustomerId();
        return ApiResponse.of(engagement.submitReview(
                customerId,
                request.organizationId(),
                request.reviewType(),
                request.storeId(),
                request.productId(),
                request.orderId(),
                request.rating(),
                request.comment()));
    }

    @GetMapping("/timeline")
    public ApiResponse<List<CustomerTimelineService.TimelineEntry>> timeline(@RequestParam UUID organizationId) {
        UUID customerId = CommerceSecurityContext.currentCustomer().getCustomerId();
        return ApiResponse.of(timeline.timeline(customerId, organizationId));
    }

    public record WishlistRequest(UUID organizationId, UUID storeId, UUID productId) {}

    public record CreateListRequest(UUID organizationId, @NotBlank String name) {}

    public record ListItemRequest(@NotNull UUID productId, BigDecimal quantity) {}

    public record ReviewRequest(
            UUID organizationId,
            @NotBlank String reviewType,
            UUID storeId,
            UUID productId,
            UUID orderId,
            @Min(1) @Max(5) int rating,
            String comment) {}
}
