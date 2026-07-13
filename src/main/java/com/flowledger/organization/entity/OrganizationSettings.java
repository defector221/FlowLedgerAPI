package com.flowledger.organization.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "organization_settings")
@Getter
@Setter
@NoArgsConstructor
public class OrganizationSettings {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID organizationId;

    @Column(nullable = false)
    private String inventoryDeductionEvent = "INVOICE_CONFIRM";

    @Column(nullable = false)
    private boolean taxInclusiveDefault;

    @Column(nullable = false)
    private boolean roundOffEnabled = true;

    private UUID defaultWarehouseId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, name = "settings_json", columnDefinition = "jsonb")
    private String settingsJson = "{}";

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void create() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void update() {
        updatedAt = Instant.now();
    }
}
