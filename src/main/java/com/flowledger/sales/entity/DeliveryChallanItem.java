package com.flowledger.sales.entity;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_challan_id")
    private DeliveryChallan deliveryChallan;

    @Column(nullable = false)
    private UUID productId;

    private String description;

    @Column(nullable = false)
    private BigDecimal quantity;

    private UUID unitId;
    private int lineOrder;
}
