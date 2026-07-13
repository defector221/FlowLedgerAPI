package com.flowledger.purchase.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "purchase_return_items")
@Getter
@Setter
@NoArgsConstructor
public class PurchaseReturnItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "purchase_return_id")
    private PurchaseReturn purchaseReturn;

    private UUID productId;
    private BigDecimal quantity, rate, lineTotal;
    private Integer lineOrder = 0;
}
