package com.flowledger.transport.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "shipment_lines")
@Getter
@Setter
@NoArgsConstructor
public class ShipmentLine {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID shipmentId;

    private UUID sourceLineId;
    private UUID productId;
    private String description;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity;

    private UUID unitId;
    private String batchNumber;
    private String serialNumber;

    @Column(nullable = false)
    private int lineOrder;
}
