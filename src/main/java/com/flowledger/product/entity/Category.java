package com.flowledger.product.entity;

import com.flowledger.common.entity.AuditedEntity;
import jakarta.persistence.*;
import java.util.*;
import lombok.*;

@Entity
@Table(
        name = "categories",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_categories_org_name",
                        columnNames = {"organization_id", "name"}))
@Getter
@Setter
@NoArgsConstructor
public class Category extends AuditedEntity {
    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(nullable = false)
    private boolean active = true;
}
