package com.flowledger.organization.entity;

import com.flowledger.common.entity.AuditedEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "branches",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_branches_org_code",
                        columnNames = {"organization_id", "code"}))
@Getter
@Setter
@NoArgsConstructor
public class Branch extends AuditedEntity {
    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "address_line1")
    private String addressLine1;

    private String city;
    private String state;

    @Column(name = "postal_code")
    private String postalCode;

    private String country = "IN";

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "is_default", nullable = false)
    private boolean defaultBranch = false;
}
