package com.flowledger.platform.approval.entity;

import com.flowledger.common.entity.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "approval_definitions")
@Getter
@Setter
@NoArgsConstructor
public class ApprovalDefinition extends AuditedEntity {
    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "levels_json", nullable = false, columnDefinition = "text")
    private String levelsJson = "[{\"level\":1,\"role\":\"ORGANIZATION_ADMIN\"}]";

    @Column(name = "min_amount", precision = 19, scale = 4)
    private BigDecimal minAmount;

    @Column(name = "max_amount", precision = 19, scale = 4)
    private BigDecimal maxAmount;
}
