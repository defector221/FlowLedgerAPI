package com.flowledger.tax.entity;

import com.flowledger.common.entity.AuditedEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "tax_categories")
@Getter
@Setter
@NoArgsConstructor
public class TaxCategory extends AuditedEntity {
    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "country_code", nullable = false, length = 3)
    private String countryCode = "IN";

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "system_defined", nullable = false)
    private boolean systemDefined = false;
}
