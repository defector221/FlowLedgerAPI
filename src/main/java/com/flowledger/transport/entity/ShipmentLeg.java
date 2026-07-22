package com.flowledger.transport.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "shipment_legs")
@Getter
@Setter
@NoArgsConstructor
public class ShipmentLeg {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID shipmentId;

    @Column(nullable = false)
    private int sequenceNo;

    private UUID transportCompanyId;
    private UUID vehicleId;
    private UUID driverId;
    private String lrNumber;
    private String consignmentNumber;
    private String vehicleNumberSnapshot;
    private String driverNameSnapshot;
    private String driverMobileSnapshot;
    private OffsetDateTime expectedDeparture;
    private OffsetDateTime expectedArrival;
    private OffsetDateTime actualDeparture;
    private OffsetDateTime actualArrival;

    @Column(columnDefinition = "text")
    private String remarks;
}
