package com.flowledger.retail.entity;

import com.flowledger.retail.domain.RetailEnums.PosSaleStatus;
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
@Table(name = "pos_sales")
@Getter
@Setter
@NoArgsConstructor
public class PosSale extends RetailAuditedEntity {
    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "counter_id")
    private UUID counterId;

    @Column(name = "terminal_id")
    private UUID terminalId;

    @Column(name = "shift_id")
    private UUID shiftId;

    @Column(name = "cashier_id")
    private UUID cashierId;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "sales_invoice_id")
    private UUID salesInvoiceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PosSaleStatus status = PosSaleStatus.DRAFT;

    @Column(name = "receipt_type", nullable = false)
    private String receiptType = "POS_RECEIPT";

    @Column(name = "bill_number")
    private String billNumber;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "discount_total", nullable = false, precision = 18, scale = 2)
    private BigDecimal discountTotal = BigDecimal.ZERO;

    @Column(name = "tax_total", nullable = false, precision = 18, scale = 2)
    private BigDecimal taxTotal = BigDecimal.ZERO;

    @Column(name = "grand_total", nullable = false, precision = 18, scale = 2)
    private BigDecimal grandTotal = BigDecimal.ZERO;

    @Column(name = "held_label")
    private String heldLabel;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;
}
