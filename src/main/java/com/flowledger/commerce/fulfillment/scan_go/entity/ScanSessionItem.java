package com.flowledger.commerce.fulfillment.scan_go.entity;

import com.flowledger.commerce.common.CommerceGlobalEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "commerce_scan_session_items")
@Getter
@Setter
@NoArgsConstructor
public class ScanSessionItem extends CommerceGlobalEntity {
    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private BigDecimal quantity = BigDecimal.ONE;

    @Column(name = "line_subtotal", nullable = false)
    private BigDecimal lineSubtotal = BigDecimal.ZERO;

    @Column(name = "line_tax", nullable = false)
    private BigDecimal lineTax = BigDecimal.ZERO;

    @Column(name = "line_total", nullable = false)
    private BigDecimal lineTotal = BigDecimal.ZERO;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "product_snapshot", nullable = false, columnDefinition = "jsonb")
    private String productSnapshot = "{}";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "price_snapshot", nullable = false, columnDefinition = "jsonb")
    private String priceSnapshot = "{}";
}
