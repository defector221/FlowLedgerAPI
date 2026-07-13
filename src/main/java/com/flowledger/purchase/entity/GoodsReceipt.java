package com.flowledger.purchase.entity;

import com.flowledger.common.entity.AuditedEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "goods_receipts")
@Getter
@Setter
@NoArgsConstructor
public class GoodsReceipt extends AuditedEntity {
    @Column(name = "grn_number")
    private String grnNumber;

    private UUID supplierId, purchaseOrderId, warehouseId;

    @Column(name = "receipt_date")
    private LocalDate receiptDate;

    private String status = "DRAFT";
    private boolean inventoryPosted;

    @Column(columnDefinition = "text")
    private String notes;

    @Version
    private Long version;

    @OneToMany(mappedBy = "receipt", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineOrder")
    private List<GoodsReceiptItem> items = new ArrayList<>();
}
