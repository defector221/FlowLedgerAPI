package com.flowledger.customer.entity;

import com.flowledger.common.entity.AuditedEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.*;

@Entity
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
public class Customer extends AuditedEntity {
    @Column(name = "customer_code", nullable = false)
    private String customerCode;
    @Column(name = "customer_name", nullable = false)
    private String customerName;
    private String companyName, gstin, pan, email, phone;
    @Column(columnDefinition = "text")
    private String billingAddress, shippingAddress, notes;
    private String city, state, stateCode, country = "India", paymentTerms;
    @Column(nullable = false)
    private BigDecimal creditLimit = BigDecimal.ZERO;
    @Column(nullable = false)
    private BigDecimal openingBalance = BigDecimal.ZERO;
    @Column(nullable = false)
    private boolean archived;
    @Version
    private Long version;
}
