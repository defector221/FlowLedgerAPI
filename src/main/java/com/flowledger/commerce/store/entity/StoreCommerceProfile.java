package com.flowledger.commerce.store.entity;

import com.flowledger.commerce.fulfillment.FulfillmentType;
import com.flowledger.commerce.store.domain.MarketplaceVisibility;
import com.flowledger.commerce.store.domain.StoreCommerceStatus;
import com.flowledger.common.entity.AuditedEntity;
import com.flowledger.common.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "store_commerce_profiles")
@Getter
@Setter
@NoArgsConstructor
public class StoreCommerceProfile extends AuditedEntity {
    @Column(name = "store_id", nullable = false, unique = true, updatable = false)
    private UUID storeId;

    @Column(name = "commerce_enabled", nullable = false)
    private boolean commerceEnabled;

    @Column(name = "accept_online_orders", nullable = false)
    private boolean acceptOnlineOrders;

    @Column(name = "supports_delivery", nullable = false)
    private boolean supportsDelivery;

    @Column(name = "supports_pickup", nullable = false)
    private boolean supportsPickup;

    @Column(name = "supports_click_collect", nullable = false)
    private boolean supportsClickCollect;

    @Column(name = "supports_scan_and_go", nullable = false)
    private boolean supportsScanAndGo;

    @Column(name = "published_to_marketplace", nullable = false)
    private boolean publishedToMarketplace;

    @Column(name = "publish_products", nullable = false)
    private boolean publishProducts;

    @Column(name = "publish_inventory", nullable = false)
    private boolean publishInventory;

    @Column(name = "publish_prices", nullable = false)
    private boolean publishPrices;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MarketplaceVisibility visibility = MarketplaceVisibility.PRIVATE;

    @Column(name = "discovery_radius")
    private BigDecimal discoveryRadius;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "allowed_category_ids", nullable = false, columnDefinition = "jsonb")
    private List<UUID> allowedCategoryIds = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StoreCommerceStatus status = StoreCommerceStatus.DRAFT;

    public void enableCommerce() {
        this.commerceEnabled = true;
        this.acceptOnlineOrders = true;
        if (status == StoreCommerceStatus.DRAFT) {
            status = StoreCommerceStatus.ACTIVE;
        }
    }

    public void disableCommerce() {
        this.commerceEnabled = false;
    }

    public void publishStoreToMarketplace() {
        this.publishedToMarketplace = true;
        this.visibility = MarketplaceVisibility.PUBLIC;
        this.acceptOnlineOrders = true;
    }

    public void unpublishStoreFromMarketplace() {
        this.publishedToMarketplace = false;
    }

    public void assertCanAcceptDigitalOrder(FulfillmentType type) {
        if (!commerceEnabled) {
            throw new BusinessException("Commerce is not enabled for this store");
        }
        switch (type) {
            case HOME_DELIVERY -> {
                if (!supportsDelivery) throw new BusinessException("Delivery not enabled");
            }
            case STORE_PICKUP -> {
                if (!supportsPickup) throw new BusinessException("Pickup not enabled");
            }
            case CLICK_AND_COLLECT -> {
                if (!supportsClickCollect) throw new BusinessException("Click & collect not enabled");
            }
            case SCAN_AND_GO -> {
                if (!supportsScanAndGo) throw new BusinessException("Scan & go not enabled");
            }
        }
    }

    public void assertCanPublishProducts() {
        if (!publishedToMarketplace || !publishProducts) {
            throw new BusinessException("Product publishing is not enabled for this store");
        }
    }

    public void assertCanPublishPrices() {
        if (!publishedToMarketplace || !publishPrices) {
            throw new BusinessException("Price publishing is not enabled for this store");
        }
    }

    public void assertCanPublishInventory() {
        if (!publishedToMarketplace || !publishInventory) {
            throw new BusinessException("Inventory publishing is not enabled for this store");
        }
    }
}
