package com.flowledger.retail.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "retail_stores")
@Getter
@Setter
@NoArgsConstructor
public class RetailStore extends RetailAuditedEntity {
    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "store_type_id")
    private UUID storeTypeId;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(columnDefinition = "text")
    private String address;

    private String city;
    private String state;
    private String phone;

    @Column(nullable = false)
    private String status = "ACTIVE";
}
