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
@Table(name = "migration_audit_logs")
@Getter
@Setter
@NoArgsConstructor
public class MigrationAuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "job_id")
    private UUID jobId;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(length = 50)
    private String module;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detail_json", nullable = false, columnDefinition = "jsonb")
    private String detailJson = "{}";

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}
