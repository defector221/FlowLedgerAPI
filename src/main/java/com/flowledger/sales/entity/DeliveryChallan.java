package com.flowledger.sales.entity;

import com.flowledger.common.entity.AuditedEntity;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import lombok.*;

@Entity
@Table(name = "delivery_challans")
@Getter
@Setter
@NoArgsConstructor
public class DeliveryChallan extends AuditedEntity {
    public enum Status {
        DRAFT,
        DELIVERED,
        CANCELLED
    }

    @Column(nullable = false)
    private String challanNumber;

    @Column(nullable = false)
    private UUID customerId;

    private UUID salesOrderId, warehouseId;

    @Column(nullable = false)
    private LocalDate challanDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.DRAFT;

    @Column(columnDefinition = "text")
    private String notes;

    @Version
    private Long version;

    @OneToMany(mappedBy = "deliveryChallan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineOrder")
    private List<DeliveryChallanItem> items = new ArrayList<>();
}
