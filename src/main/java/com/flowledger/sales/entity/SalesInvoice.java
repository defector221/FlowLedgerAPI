package com.flowledger.sales.entity;

import com.flowledger.accounting.domain.AccountingStatus;
import com.flowledger.common.entity.AuditedEntity;
import jakarta.persistence.*;
import java.math.*;
import java.time.*;
import java.util.*;
import lombok.*;

@Entity
@Table(name = "sales_invoices")
@Getter
@Setter
@NoArgsConstructor
public class SalesInvoice extends AuditedEntity {
    public enum Status {
        DRAFT,
        CONFIRMED,
        PARTIALLY_PAID,
        PAID,
        OVERDUE,
        CANCELLED
    }

    @Column(nullable = false)
    private String invoiceNumber = "";

    @Column(nullable = false)
    private LocalDate invoiceDate;

    private LocalDate dueDate;

    @Column(nullable = false)
    private UUID customerId;

    private UUID salesOrderId, deliveryChallanId, warehouseId;
    @Column(columnDefinition = "text")
    private String billingAddress, shippingAddress, notes, termsAndConditions;
    private String placeOfSupply, customerGstin, amountInWords;
    private boolean reverseCharge, taxInclusive, inventoryPosted;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.DRAFT;

    @Column(nullable = false)
    private String paymentStatus = "UNPAID";

    @Column(nullable = false)
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
    private UUID templateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "accounting_status", nullable = false)
    private AccountingStatus accountingStatus = AccountingStatus.NOT_POSTED;

    @Column(name = "posted_journal_entry_id")
    private UUID postedJournalEntryId;

    @Column(name = "accounting_posted_at")
    private OffsetDateTime accountingPostedAt;

    @Version
    private Long version;

    @OneToMany(mappedBy = "salesInvoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineOrder")
    private List<SalesInvoiceItem> items = new ArrayList<>();
}
