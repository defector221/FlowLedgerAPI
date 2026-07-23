package com.flowledger.transport.entity;

import com.flowledger.transport.domain.TransportEnums.FreightPayer;
import com.flowledger.transport.domain.TransportEnums.ShipmentStatus;
import com.flowledger.transport.domain.TransportEnums.TransportMode;
import com.flowledger.transport.domain.TransportEnums.TransportType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "shipments")
@Getter
@Setter
@NoArgsConstructor
public class Shipment extends TransportAuditedEntity {
    @Column(nullable = false)
    private String shipmentNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShipmentStatus status = ShipmentStatus.DRAFT;

    private String sourceDocumentType;
    private UUID sourceDocumentId;

    @Column(nullable = false)
    private boolean transportRequired;

    @Enumerated(EnumType.STRING)
    private TransportMode transportMode;

    @Enumerated(EnumType.STRING)
    private TransportType transportType;

    private UUID transportCompanyId;
    private UUID fromWarehouseId;
    private String shipToPartyType;
    private UUID shipToPartyId;

    @Column(columnDefinition = "text")
    private String shipToAddress;

    private OffsetDateTime expectedDispatchDate;
    private OffsetDateTime expectedDeliveryDate;
    private OffsetDateTime actualDispatchDate;
    private OffsetDateTime actualDeliveryDate;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal freightCharges = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    private FreightPayer freightPaidBy;

    @Column(columnDefinition = "text")
    private String insuranceDetails;

    private String gpsTrackingUrl;
    private String ewayBillNumber;
    private String einvoiceReference;

    @Column(columnDefinition = "text")
    private String remarks;

    private String priority;

    @Column(precision = 19, scale = 4)
    private BigDecimal totalDistance;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal fuelChargesTotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal tollChargesTotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal otherChargesTotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal grandTotal = BigDecimal.ZERO;
}
