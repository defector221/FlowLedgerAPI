package com.flowledger.platform.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "modules")
@Getter
@Setter
@NoArgsConstructor
public class PlatformModule {
    @Id
    @Column(length = 64)
    private String code;

    @Column(nullable = false, length = 128)
    private String displayName;

    @Column(columnDefinition = "text")
    private String description;

    @Column(length = 64)
    private String icon;

    @Column(nullable = false, length = 64)
    private String category = "GENERAL";

    @Column(nullable = false, length = 32)
    private String version = "1.0.0";

    @Column(name = "is_core", nullable = false)
    private boolean core;

    @Column(nullable = false)
    private boolean enabledByDefault;

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
