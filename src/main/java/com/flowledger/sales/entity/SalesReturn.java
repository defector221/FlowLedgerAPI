package com.flowledger.sales.entity;

import com.flowledger.common.entity.AuditedEntity;
import jakarta.persistence.*;
import java.math.*;
import java.time.*;
import java.util.*;
import lombok.*;

@Entity
@Table(name = "sales_returns")
@Getter
@Setter
@NoArgsConstructor
public class SalesReturn extends AuditedEntity {
    @Column(nullable = false)
    private String returnNumber;

    @Column(nullable = false)
    private UUID salesInvoiceId, customerId;

    @Column(nullable = false)
    private LocalDate returnDate;

    @Column(nullable = false)
    private String status = "DRAFT";

    @Column(nullable = false)
    private BigDecimal grandTotal = BigDecimal.ZERO;

    private boolean inventoryPosted;

    @Column(columnDefinition = "text")
    private String notes;

    @Version
    private Long version;

    @OneToMany(mappedBy = "salesReturn", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SalesReturnItem> items = new ArrayList<>();
}
