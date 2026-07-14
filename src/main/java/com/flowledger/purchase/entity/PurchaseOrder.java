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
@Table(name = "purchase_orders")
@Getter
@Setter
@NoArgsConstructor
public class PurchaseOrder extends AuditedEntity {
    @Column(name = "po_number")
    private String poNumber;

    @Column(name = "supplier_id")
    private UUID supplierId;

    @Column(name = "order_date")
    private LocalDate orderDate;

    private LocalDate expectedDeliveryDate;
    private String status = "DRAFT";
    private BigDecimal subtotal = BigDecimal.ZERO,
            discountTotal = BigDecimal.ZERO,
            taxTotal = BigDecimal.ZERO,
            grandTotal = BigDecimal.ZERO;
    @Column(columnDefinition = "text")
    private String termsAndConditions, notes;

    @Version
    private Long version;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineOrder")
    private List<PurchaseOrderItem> items = new ArrayList<>();
}
