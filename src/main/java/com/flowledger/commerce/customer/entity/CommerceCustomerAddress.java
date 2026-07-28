package com.flowledger.commerce.customer.entity;

import com.flowledger.commerce.common.CommerceGlobalEntity;
import com.flowledger.commerce.customer.domain.AddressType;
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
@Table(name = "commerce_customer_addresses")
@Getter
@Setter
@NoArgsConstructor
public class CommerceCustomerAddress extends CommerceGlobalEntity {
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "address_type", nullable = false)
    private AddressType addressType = AddressType.HOME;

    private String house;
    private String street;
    private String landmark;
    private String city;
    private String district;
    private String state;
    private String country = "IN";
    private String pincode;
    private BigDecimal latitude;
    private BigDecimal longitude;

    @Column(name = "is_default", nullable = false)
    private boolean defaultAddress;
}
