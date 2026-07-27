package com.flowledger.migration.entity;

import com.flowledger.migration.domain.ImportJobStatus;
import com.flowledger.migration.domain.ImportModule;
import com.flowledger.migration.domain.ImportSourceType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "import_jobs")
@Getter
@Setter
@NoArgsConstructor
public class ImportJob {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ImportModule module;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30)
    private ImportSourceType sourceType = ImportSourceType.CSV;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ImportJobStatus status = ImportJobStatus.PENDING;

    @Column(name = "file_object_key", length = 500)
    private String fileObjectKey;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "mapping_profile_id")
    private UUID mappingProfileId;

    @Column(name = "total_rows", nullable = false)
    private int totalRows;

    @Column(name = "processed_rows", nullable = false)
    private int processedRows;

    @Column(name = "success_rows", nullable = false)
    private int successRows;

    @Column(name = "error_rows", nullable = false)
    private int errorRows;

    @Column(name = "skipped_rows", nullable = false)
    private int skippedRows;

    @Column(name = "progress_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal progressPct = BigDecimal.ZERO;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "columns_json", nullable = false, columnDefinition = "jsonb")
    private String columnsJson = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "mapping_json", nullable = false, columnDefinition = "jsonb")
    private String mappingJson = "{}";

    @Column(name = "error_summary", columnDefinition = "text")
    private String errorSummary;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

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
