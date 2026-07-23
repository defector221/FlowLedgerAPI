package com.flowledger.retail.entity;

import com.flowledger.retail.domain.RetailEnums.PromoType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "retail_promotions")
@Getter
@Setter
@NoArgsConstructor
public class RetailPromotion extends RetailAuditedEntity {
    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "promo_type", nullable = false)
    private PromoType promoType;

    @Column(name = "discount_percent", precision = 8, scale = 4)
    private BigDecimal discountPercent;

    @Column(name = "discount_amount", precision = 18, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "buy_qty", precision = 18, scale = 4)
    private BigDecimal buyQty;

    @Column(name = "get_qty", precision = 18, scale = 4)
    private BigDecimal getQty;

    @Column(name = "min_bill_amount", precision = 18, scale = 2)
    private BigDecimal minBillAmount;

    @Column(name = "coupon_code")
    private String couponCode;

    @Column(name = "starts_at")
    private OffsetDateTime startsAt;

    @Column(name = "ends_at")
    private OffsetDateTime endsAt;

    @Column(name = "store_id")
    private UUID storeId;

    @Column(name = "brand_id")
    private UUID brandId;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "product_id")
    private UUID productId;

    @Column(nullable = false)
    private boolean active = true;
}
