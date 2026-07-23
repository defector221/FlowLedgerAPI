package com.flowledger.organization.entity;

import com.flowledger.common.entity.AuditableEntity;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Table(name = "organizations")
@Getter
@Setter
@NoArgsConstructor
public class Organization extends AuditableEntity {
    @Column(nullable = false)
    private String name;

    private String legalName;
    private String logoObjectKey;
    private String gstin;
    private String pan;
    private String cin;
    private String email;
    private String phone;
    private String website;

    @Column(columnDefinition = "text")
    private String billingAddress;

    @Column(columnDefinition = "text")
    private String shippingAddress;

    private String city;
    private String state;
    private String stateCode;
    private String country = "India";
    private String currency = "INR";
    private String financialYearStart = "04-01";
    private String invoicePrefix = "INV";
    private String purchaseInvoicePrefix = "PINV";
    private String quotationPrefix = "QT";
    private String salesOrderPrefix = "SO";
    private String deliveryChallanPrefix = "DC";
    private String purchaseOrderPrefix = "PO";
    private String paymentPrefix = "PAY";
    private String invoiceNumberFormat = "{PREFIX}/{FY}/{SEQ:6}";
    private java.util.UUID defaultTaxRateId;
    private String bankName;
    private String bankAccountNumber;
    private String bankIfsc;
    private String bankBranch;
    private String upiId;
    private String paymentTerms;
    private boolean allowNegativeStock;
    private boolean active = true;

    @Column(nullable = false)
    private boolean onboardingCompleted;

    private Instant onboardingCompletedAt;

    @Column(nullable = false, length = 32)
    private String editionCode = "PROFESSIONAL";
}
