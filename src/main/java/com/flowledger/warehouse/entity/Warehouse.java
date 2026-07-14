package com.flowledger.warehouse.entity;

import com.flowledger.common.entity.AuditedEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "warehouses",
        uniqueConstraints =
                @UniqueConstraint(name = "uq_warehouses_org_code", columnNames = {"organization_id", "warehouse_code"}))
@Getter
@Setter
@NoArgsConstructor
public class Warehouse extends AuditedEntity {
    @Column(name = "warehouse_code", nullable = false)
    private String warehouseCode;

    @Column(name = "warehouse_name", nullable = false)
    private String warehouseName;

    @Column(columnDefinition = "text")
    private String address;

    @Column(name = "contact_person")
    private String contactPerson;

    private String phone;

    @Column(name = "is_default", nullable = false)
    private boolean defaultWarehouse;

    @Column(nullable = false)
    private boolean active = true;

    @Version
    private Long version;
}
