package com.flowledger.payment.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "payment_allocations")
@Getter
@Setter
@NoArgsConstructor
public class PaymentAllocation {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    private String documentType;
    private UUID documentId;
    private BigDecimal allocatedAmount;

    @Column(updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void created() {
        createdAt = OffsetDateTime.now();
    }
}
