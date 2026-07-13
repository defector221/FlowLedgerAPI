package com.flowledger.supplier.entity;

import com.flowledger.common.entity.AuditedEntity;
import jakarta.persistence.*;
import java.math.*;
import lombok.*;

@Entity
@Table(name = "suppliers")
@Getter
@Setter
@NoArgsConstructor
public class Supplier extends AuditedEntity {
    @Column(name = "supplier_code", nullable = false)
    private String supplierCode;

    @Column(name = "supplier_name", nullable = false)
    private String supplierName;

    private String companyName, gstin, pan, email, phone;
    @Column(columnDefinition = "text")
    private String billingAddress, shippingAddress, notes;
    private String city, state, stateCode, country = "India", paymentTerms, bankName, bankAccountNumber, bankIfsc;

    @Column(nullable = false)
    private BigDecimal openingBalance = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean archived;

    @Version
    private Long version;
}
