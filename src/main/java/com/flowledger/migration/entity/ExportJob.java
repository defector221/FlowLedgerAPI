package com.flowledger.migration.entity;

import com.flowledger.migration.domain.ExportFormat;
import com.flowledger.migration.domain.ExportJobStatus;
import com.flowledger.migration.domain.ImportModule;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "export_jobs")
@Getter
@Setter
@NoArgsConstructor
public class ExportJob {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ImportModule module;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExportFormat format = ExportFormat.CSV;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ExportJobStatus status = ExportJobStatus.PENDING;

    @Column(name = "file_object_key", length = 500)
    private String fileObjectKey;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "total_rows", nullable = false)
    private int totalRows;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}
