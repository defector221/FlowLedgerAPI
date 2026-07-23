package com.flowledger.retail.entity;

import com.flowledger.retail.domain.RetailEnums.PriceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "retail_price_lists")
@Getter
@Setter
@NoArgsConstructor
public class RetailPriceList extends RetailAuditedEntity {
    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_type", nullable = false)
    private PriceType priceType = PriceType.RETAIL;

    @Column(nullable = false)
    private String currency = "INR";

    @Column(nullable = false)
    private boolean active = true;
}
