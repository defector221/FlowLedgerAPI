package com.flowledger.retail.entity;

import com.flowledger.retail.domain.RetailEnums.PosSaleStatus;
import com.flowledger.retail.domain.RetailEnums.RefundMode;
import com.flowledger.retail.domain.RetailEnums.ReturnReason;
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

@Entity
@Table(name = "pos_returns")
@Getter
@Setter
@NoArgsConstructor
public class PosReturn extends RetailAuditedEntity {
    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "original_pos_sale_id")
    private UUID originalPosSaleId;

    @Column(name = "original_invoice_id")
    private UUID originalInvoiceId;

    @Column(name = "sales_return_id")
    private UUID salesReturnId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PosSaleStatus status = PosSaleStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReturnReason reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "refund_mode", nullable = false)
    private RefundMode refundMode = RefundMode.REFUND;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "total_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;
}
