package com.flowledger.inventory.entity;

import com.flowledger.common.entity.AuditedEntity;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;

@Entity
@Table(
        name = "serial_numbers",
        uniqueConstraints = @UniqueConstraint(columnNames = {"organization_id", "product_id", "serial_number"}))
@Getter
@Setter
@NoArgsConstructor
public class SerialNumber extends AuditedEntity {
    public enum Status {
        IN_STOCK,
        SOLD,
        DAMAGED,
        RETURNED
    }

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "warehouse_id")
    private UUID warehouseId;

    @Column(name = "serial_number", nullable = false)
    private String serialNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.IN_STOCK;
}
