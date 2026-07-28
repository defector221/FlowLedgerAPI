package com.flowledger.commerce.fulfillment.scan_go.entity;

import com.flowledger.commerce.common.CommerceGlobalEntity;
import com.flowledger.commerce.fulfillment.scan_go.domain.ScanSessionStatus;
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
@Table(name = "commerce_scan_sessions")
@Getter
@Setter
@NoArgsConstructor
public class ScanSession extends CommerceGlobalEntity {
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScanSessionStatus status = ScanSessionStatus.ACTIVE;

    @Column(nullable = false)
    private String currency = "INR";

    @Column(nullable = false)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "tax_total", nullable = false)
    private BigDecimal taxTotal = BigDecimal.ZERO;

    @Column(name = "grand_total", nullable = false)
    private BigDecimal grandTotal = BigDecimal.ZERO;

    @Column(name = "item_count", nullable = false)
    private int itemCount;

    @Column(name = "entry_at", nullable = false)
    private OffsetDateTime entryAt = OffsetDateTime.now();

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "commerce_order_id")
    private UUID commerceOrderId;
}
