package com.flowledger.finance.dimension.entity;

import com.flowledger.common.entity.AuditedEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "departments",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_departments_org_code",
                        columnNames = {"organization_id", "code"}))
@Getter
@Setter
@NoArgsConstructor
public class Department extends AuditedEntity {
    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false)
    private boolean active = true;
}
