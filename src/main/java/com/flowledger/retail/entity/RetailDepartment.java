package com.flowledger.retail.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "retail_departments")
@Getter
@Setter
@NoArgsConstructor
public class RetailDepartment extends RetailAuditedEntity {
    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;
}
