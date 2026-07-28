package com.flowledger.tax.entity;

import com.flowledger.tax.domain.TaxProviderCode;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "organization_tax_settings")
@Getter
@Setter
@NoArgsConstructor
public class OrganizationTaxSettings {
    @Id
    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "default_tax_category_id")
    private UUID defaultTaxCategoryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_code", nullable = false, length = 30)
    private TaxProviderCode providerCode = TaxProviderCode.IndiaGST;

    @Column(name = "rounding_scale", nullable = false)
    private int roundingScale = 2;

    @Column(name = "rounding_mode", nullable = false, length = 20)
    private String roundingMode = "HALF_UP";

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    void created() {
        var now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void updated() {
        updatedAt = OffsetDateTime.now();
    }
}
