package com.flowledger.labels.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "label_template_fields")
@Getter
@Setter
@NoArgsConstructor
public class LabelTemplateField {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "template_id", nullable = false, updatable = false)
    private UUID templateId;

    @Column(name = "field_type", nullable = false, length = 40)
    private String fieldType;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal x = BigDecimal.ZERO;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal y = BigDecimal.ZERO;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal width = BigDecimal.TEN;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal height = new BigDecimal("5");

    @Column(name = "font_size", precision = 6, scale = 2)
    private BigDecimal fontSize;

    @Column(name = "binding_key", length = 100)
    private String bindingKey;

    @Column(name = "z_index", nullable = false)
    private int zIndex;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
