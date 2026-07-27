package com.flowledger.migration.entity;

import com.flowledger.migration.domain.ImportRowStatus;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "import_row_results")
@Getter
@Setter
@NoArgsConstructor
public class ImportRowResult {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "row_number", nullable = false)
    private int rowNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ImportRowStatus status = ImportRowStatus.PENDING;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_json", nullable = false, columnDefinition = "jsonb")
    private String rawJson = "{}";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "normalized_json", nullable = false, columnDefinition = "jsonb")
    private String normalizedJson = "{}";

    @Column(name = "entity_type", length = 50)
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    @Column(columnDefinition = "text")
    private String message;

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
