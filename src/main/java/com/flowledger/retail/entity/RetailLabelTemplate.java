package com.flowledger.retail.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
}
