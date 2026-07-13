package com.flowledger.template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowledger.common.entity.AuditedEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "invoice_templates")
@Getter
@Setter
@NoArgsConstructor
public class InvoiceTemplate extends AuditedEntity {
    @Column(name = "template_name")
    String templateName;

    @Column(name = "document_type", nullable = false)
    String documentType = "SALES_INVOICE";

    String presetKey;
    boolean isDefault;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config_json", columnDefinition = "jsonb")
    JsonNode configJson;

    @Version
    Long version;
}
