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
@Table(name = "purchase_returns")
@Getter
@Setter
@NoArgsConstructor
public class PurchaseReturn extends AuditedEntity {
    @Column(name = "return_number")
    private String returnNumber;

    private UUID purchaseInvoiceId, supplierId;
    private LocalDate returnDate;
    private String status = "DRAFT";
    private BigDecimal grandTotal = BigDecimal.ZERO;
    private boolean inventoryPosted;

    @Column(columnDefinition = "text")
    private String notes;

    @Version
    private Long version;

    @OneToMany(mappedBy = "purchaseReturn", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PurchaseReturnItem> items = new ArrayList<>();
}
