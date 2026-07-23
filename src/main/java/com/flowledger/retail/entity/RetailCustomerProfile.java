package com.flowledger.retail.entity;

import com.flowledger.common.entity.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "retail_customer_profiles")
@Getter
@Setter
@NoArgsConstructor
public class RetailCustomerProfile extends AuditedEntity {
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "membership_code")
    private String membershipCode;

    private LocalDate birthday;
    private LocalDate anniversary;
}
