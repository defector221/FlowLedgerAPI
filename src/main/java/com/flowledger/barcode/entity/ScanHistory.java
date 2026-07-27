package com.flowledger.barcode.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "scan_history")
@Getter
@Setter
@NoArgsConstructor
public class ScanHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 150)
    private String barcode;

    @Column(nullable = false, length = 20)
    private String source = "SCANNER";

    @Column(length = 60)
    private String module;

    @Column(name = "resolved_product_id")
    private UUID resolvedProductId;

    @Column(name = "resolved_variant_id")
    private UUID resolvedVariantId;

    @Column(nullable = false)
    private boolean success;

    @Column(name = "failure_reason", length = 100)
    private String failureReason;

    @Column(name = "scanned_at", nullable = false)
    private OffsetDateTime scannedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @PrePersist
    void onCreate() {
        if (scannedAt == null) {
            scannedAt = OffsetDateTime.now();
        }
    }
}
