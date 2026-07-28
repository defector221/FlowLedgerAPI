package com.flowledger.retail.entity;

import com.flowledger.retail.domain.StoreType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "retail_stores")
@Getter
@Setter
@NoArgsConstructor
public class RetailStore extends RetailAuditedEntity {
    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "store_type_id")
    private UUID storeTypeId;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(columnDefinition = "text")
    private String address;

    private String city;
    private String state;
    private String phone;

    @Column(nullable = false)
    private String status = "ACTIVE";

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "manager_id")
    private UUID managerId;

    private String email;

    @Column(name = "postal_code")
    private String postalCode;

    private String country = "IN";

    @Enumerated(EnumType.STRING)
    @Column(name = "store_type", nullable = false)
    private StoreType storeType = StoreType.RETAIL;

    @Column(name = "default_price_list_id")
    private UUID defaultPriceListId;

    @Column(name = "default_tax_profile_id")
    private UUID defaultTaxProfileId;

    @Column(name = "default_currency")
    private String defaultCurrency;

    private String timezone;

    private BigDecimal latitude;
    private BigDecimal longitude;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "opening_hours", columnDefinition = "jsonb")
    private String openingHours;

    @Column(name = "allow_negative_stock", nullable = false)
    private boolean allowNegativeStock;

    @Column(name = "allow_offline_pos", nullable = false)
    private boolean allowOfflinePos;

    @Column(name = "enable_click_and_collect", nullable = false)
    private boolean enableClickAndCollect;

    @Column(name = "enable_loyalty", nullable = false)
    private boolean enableLoyalty;

    @Column(name = "enable_gift_card", nullable = false)
    private boolean enableGiftCard;
}
