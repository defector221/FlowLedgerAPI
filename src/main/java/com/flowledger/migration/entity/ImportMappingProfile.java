package com.flowledger.migration.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "import_mapping_profiles")
@Getter
@Setter
@NoArgsConstructor
public class ImportMappingProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 50)
    private String module;

    @Column(name = "source_label", length = 100)
    private String sourceLabel;

    @Column(name = "auto_create_missing", nullable = false)
    private boolean autoCreateMissing;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "mappings_json", nullable = false, columnDefinition = "jsonb")
    private String mappingsJson = "[]";

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        var now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
