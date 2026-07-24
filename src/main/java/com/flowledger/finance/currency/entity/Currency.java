package com.flowledger.finance.currency.entity;

import com.flowledger.common.entity.AuditedEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "currencies",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_currencies_org_code",
                        columnNames = {"organization_id", "code"}))
@Getter
@Setter
@NoArgsConstructor
public class Currency extends AuditedEntity {
    @Column(nullable = false, length = 10)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 10)
    private String symbol;

    @Column(name = "decimal_places", nullable = false)
    private int decimalPlaces = 2;

    @Column(name = "is_base", nullable = false)
    private boolean base = false;

    @Column(nullable = false)
    private boolean active = true;
}
