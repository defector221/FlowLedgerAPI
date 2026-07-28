package com.flowledger.commerce.order.entity;

import com.flowledger.commerce.common.CommerceGlobalEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "commerce_order_return_lines")
@Getter
@Setter
@NoArgsConstructor
public class CommerceOrderReturnLine extends CommerceGlobalEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "return_id", nullable = false)
    private CommerceOrderReturn orderReturn;

    @Column(name = "order_line_id", nullable = false, updatable = false)
    private UUID orderLineId;

    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Column(nullable = false)
    private BigDecimal quantity;

    @Column(nullable = false)
    private BigDecimal rate;

    @Column(name = "line_total", nullable = false)
    private BigDecimal lineTotal;
}
