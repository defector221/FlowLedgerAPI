package com.flowledger.retail.entity;

import com.flowledger.common.entity.AuditedEntity;
import com.flowledger.retail.domain.RetailEnums.PaymentMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "pos_sale_payments")
@Getter
@Setter
@NoArgsConstructor
public class PosSalePayment extends AuditedEntity {
    @Column(name = "pos_sale_id", nullable = false)
    private UUID posSaleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_mode", nullable = false)
    private PaymentMode paymentMode;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "payment_id")
    private UUID paymentId;

    private String reference;
}
