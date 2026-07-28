package com.flowledger.commerce.order.entity;

import com.flowledger.commerce.common.CommerceGlobalEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "commerce_order_lines")
@Getter
@Setter
@NoArgsConstructor
public class CommerceOrderLine extends CommerceGlobalEntity {
    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Column(name = "variant_id")
    private UUID variantId;

    @Column(nullable = false)
    private BigDecimal quantity;

    @Column(name = "line_subtotal", nullable = false)
    private BigDecimal lineSubtotal = BigDecimal.ZERO;

    @Column(name = "line_tax", nullable = false)
    private BigDecimal lineTax = BigDecimal.ZERO;

    @Column(name = "line_total", nullable = false)
    private BigDecimal lineTotal = BigDecimal.ZERO;

    @Column(name = "product_snapshot", nullable = false, columnDefinition = "jsonb")
    private String productSnapshot = "{}";

    @Column(name = "price_snapshot", nullable = false, columnDefinition = "jsonb")
    private String priceSnapshot = "{}";

    @Column(name = "tax_snapshot", nullable = false, columnDefinition = "jsonb")
    private String taxSnapshot = "{}";

    @Column(name = "promotion_snapshot", nullable = false, columnDefinition = "jsonb")
    private String promotionSnapshot = "{}";
}
