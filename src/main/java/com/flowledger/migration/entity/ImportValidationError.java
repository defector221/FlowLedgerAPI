package com.flowledger.migration.entity;

import com.flowledger.migration.domain.ValidationSeverity;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "import_validation_errors")
@Getter
@Setter
@NoArgsConstructor
public class ImportValidationError {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "row_number", nullable = false)
    private int rowNumber;

    @Column(length = 100)
    private String field;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, columnDefinition = "text")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ValidationSeverity severity = ValidationSeverity.ERROR;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}
