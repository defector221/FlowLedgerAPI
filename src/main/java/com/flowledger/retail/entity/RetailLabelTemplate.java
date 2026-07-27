package com.flowledger.retail.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "retail_label_templates")
@Getter
@Setter
@NoArgsConstructor
public class RetailLabelTemplate extends RetailAuditedEntity {
    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "label_type", nullable = false)
    private String labelType = "SHELF";

    @Column(name = "template_body", nullable = false, columnDefinition = "text")
    private String templateBody;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "canvas_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> canvasJson = Map.of();

    @Column(name = "paper_size", length = 40)
    private String paperSize = "50x25mm";

    @Column(nullable = false)
    private int dpi = 203;
}
