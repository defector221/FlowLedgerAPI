package com.flowledger.commerce.capability.entity;

import com.flowledger.common.entity.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "merchant_capability_profiles")
@Getter
@Setter
@NoArgsConstructor
public class MerchantCapabilityProfile extends AuditedEntity {
    @Column(name = "supports_marketplace", nullable = false)
    private boolean supportsMarketplace;

    @Column(name = "supports_delivery", nullable = false)
    private boolean supportsDelivery;

    @Column(name = "supports_pickup", nullable = false)
    private boolean supportsPickup;

    @Column(name = "supports_click_collect", nullable = false)
    private boolean supportsClickCollect;

    @Column(name = "supports_scan_and_go", nullable = false)
    private boolean supportsScanAndGo;

    @Column(name = "supports_scheduled_delivery", nullable = false)
    private boolean supportsScheduledDelivery;

    @Column(name = "supports_scheduled_pickup", nullable = false)
    private boolean supportsScheduledPickup;

    @Column(name = "supports_wallet", nullable = false)
    private boolean supportsWallet;

    @Column(name = "supports_coupons", nullable = false)
    private boolean supportsCoupons;

    @Column(name = "supports_loyalty", nullable = false)
    private boolean supportsLoyalty;

    @Column(name = "supports_recommendations", nullable = false)
    private boolean supportsRecommendations;

    @Column(name = "supports_reviews", nullable = false)
    private boolean supportsReviews;

    @Column(name = "supports_returns", nullable = false)
    private boolean supportsReturns;

    @Column(name = "supports_gift_cards", nullable = false)
    private boolean supportsGiftCards;
}
