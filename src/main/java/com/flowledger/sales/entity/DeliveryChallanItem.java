package com.flowledger.sales.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.math.*;
import java.util.*;
import lombok.*;

@Entity
@Table(name = "delivery_challan_items")
@Getter
@Setter
@NoArgsConstructor
public class DeliveryChallanItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_challan_id")
    private DeliveryChallan deliveryChallan;

    @Column(nullable = false)
    private UUID productId;

    private String description;

    @Column(nullable = false)
    private BigDecimal quantity;

    @Column(nullable = false)
    private BigDecimal quantityDispatched = BigDecimal.ZERO;

    @Transient
    public BigDecimal getQuantityRemaining() {
        return quantity.subtract(quantityDispatched == null ? BigDecimal.ZERO : quantityDispatched);
    }

    private UUID unitId;
    private int lineOrder;
}
