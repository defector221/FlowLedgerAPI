package com.flowledger.purchase.entity;

import com.flowledger.common.entity.AuditedEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "purchase_invoices")
@Getter
@Setter
@NoArgsConstructor
public class PurchaseInvoice extends AuditedEntity {
    @Column(name = "invoice_number")
    private String invoiceNumber;

    private String supplierInvoiceNumber;
    private LocalDate invoiceDate, dueDate;
    private UUID supplierId, purchaseOrderId, goodsReceiptId, warehouseId;
    private String placeOfSupply, supplierGstin, status = "DRAFT", paymentStatus = "UNPAID";
    private boolean reverseCharge, taxInclusive;
    private BigDecimal subtotal = BigDecimal.ZERO,
            discountTotal = BigDecimal.ZERO,
            taxableAmount = BigDecimal.ZERO,
            cgstTotal = BigDecimal.ZERO,
            sgstTotal = BigDecimal.ZERO,
            igstTotal = BigDecimal.ZERO,
            cessTotal = BigDecimal.ZERO,
            shippingCharges = BigDecimal.ZERO,
            additionalCharges = BigDecimal.ZERO,
            roundOff = BigDecimal.ZERO,
            grandTotal = BigDecimal.ZERO,
            amountPaid = BigDecimal.ZERO,
            outstandingAmount = BigDecimal.ZERO;
    @Column(columnDefinition = "text")
    private String notes, termsAndConditions;

    @Version
    private Long version;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineOrder")
    private List<PurchaseInvoiceItem> items = new ArrayList<>();
}
