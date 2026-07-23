package com.flowledger.platform.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "module_features")
@Getter
@Setter
@NoArgsConstructor
public class ModuleFeature {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "module_code", nullable = false, length = 64)
    private String moduleCode;

    @Column(name = "feature_code", nullable = false, length = 64)
    private String featureCode;

    @Column(nullable = false, length = 128)
    private String displayName;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false)
    private boolean enabledByDefault = true;

    @Column(nullable = false, length = 32)
    private String status = "ACTIVE";

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
