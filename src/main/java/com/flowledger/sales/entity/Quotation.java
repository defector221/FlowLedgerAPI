package com.flowledger.sales.entity;

import com.flowledger.common.entity.AuditedEntity;
import jakarta.persistence.*;
import java.math.*;
import java.time.*;
import java.util.*;
import lombok.*;

@Entity
@Table(name = "quotations")
@Getter
@Setter
@NoArgsConstructor
public class Quotation extends AuditedEntity {
    public enum Status {
        DRAFT,
        ACCEPTED,
        CONVERTED,
        EXPIRED,
        CANCELLED
    }

    @Column(nullable = false)
    private String quotationNumber;

    @Column(nullable = false)
    private UUID customerId;

    @Column(nullable = false)
    private LocalDate quotationDate;

    private LocalDate expiryDate;
    @Column(columnDefinition = "text")
    private String billingAddress, shippingAddress, termsAndConditions, notes;
    private String placeOfSupply;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.DRAFT;

    @Column(nullable = false)
    private BigDecimal subtotal = BigDecimal.ZERO,
            discountTotal = BigDecimal.ZERO,
            taxTotal = BigDecimal.ZERO,
            grandTotal = BigDecimal.ZERO;
    private UUID convertedToOrderId;

    @Version
    private Long version;

    @OneToMany(mappedBy = "quotation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineOrder")
    private List<QuotationItem> items = new ArrayList<>();
}
