package com.flowledger.platform.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "organization_modules")
@Getter
@Setter
@NoArgsConstructor
public class OrganizationModule {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID organizationId;

    @Column(name = "module_code", nullable = false, length = 64)
    private String moduleCode;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false)
    private boolean licensed = true;

    @Column(nullable = false)
    private boolean trial;

    private Instant expiresAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String configuration = "{}";

    private UUID createdBy;
    private UUID updatedBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (configuration == null) {
            configuration = "{}";
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public boolean isEffectivelyEnabled() {
        if (!enabled || !licensed) {
            return false;
        }
        return expiresAt == null || expiresAt.isAfter(Instant.now());
    }
}
