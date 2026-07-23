package com.flowledger.retail.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "retail_terminals")
@Getter
@Setter
@NoArgsConstructor
public class RetailTerminal extends RetailAuditedEntity {
    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "counter_id")
    private UUID counterId;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String status = "ACTIVE";
}
