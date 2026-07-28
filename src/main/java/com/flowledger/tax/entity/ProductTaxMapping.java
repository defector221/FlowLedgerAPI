package com.flowledger.tax.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "product_tax_mapping")
@Getter
@Setter
@NoArgsConstructor
public class ProductTaxMapping {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "tax_category_id", nullable = false)
    private UUID taxCategoryId;

    @Column(name = "inherit_from_category", nullable = false)
    private boolean inheritFromCategory = false;

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
