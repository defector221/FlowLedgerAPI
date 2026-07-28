package com.flowledger.commerce.order.entity;

import com.flowledger.commerce.common.CommerceGlobalEntity;
import com.flowledger.commerce.order.domain.CommerceOrderReturnStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "commerce_order_returns")
@Getter
@Setter
@NoArgsConstructor
public class CommerceOrderReturn extends CommerceGlobalEntity {
    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "commerce_order_id", nullable = false, updatable = false)
    private UUID commerceOrderId;

    @Column(name = "fulfillment_order_id")
    private UUID fulfillmentOrderId;

    @Column(name = "erp_sales_return_id")
    private UUID erpSalesReturnId;

    @Column(name = "return_number")
    private String returnNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CommerceOrderReturnStatus status = CommerceOrderReturnStatus.CONFIRMED;

    @Column(columnDefinition = "text")
    private String notes;

    @jakarta.persistence.OneToMany(mappedBy = "orderReturn", cascade = jakarta.persistence.CascadeType.ALL, orphanRemoval = true)
    private List<CommerceOrderReturnLine> lines = new ArrayList<>();
}
