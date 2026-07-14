package com.flowledger.emailtemplate.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowledger.common.entity.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "email_templates")
@Getter
@Setter
@NoArgsConstructor
public class EmailTemplate extends AuditedEntity {
    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String subject = "";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "design_json", columnDefinition = "jsonb")
    private JsonNode designJson;

    @Column(columnDefinition = "text")
    private String html;

    @Version
    private Long version;
}
