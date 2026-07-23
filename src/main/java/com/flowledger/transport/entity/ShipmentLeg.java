package com.flowledger.transport.entity;

import com.flowledger.transport.domain.TransportEnums.ShipmentLegStatus;
import com.flowledger.transport.domain.TransportEnums.TransportMode;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "shipment_legs")
@Getter
@Setter
@NoArgsConstructor
public class ShipmentLeg extends TransportAuditedEntity {
    @Column(nullable = false)
    private UUID shipmentId;

    @Column(nullable = false)
    private int sequenceNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShipmentLegStatus status = ShipmentLegStatus.PLANNED;

    @Enumerated(EnumType.STRING)
    private TransportMode transportMode;

    private UUID transportCompanyId;
    private UUID vehicleId;
    private UUID driverId;
    private String lrNumber;
    private String consignmentNumber;
    private String vehicleNumberSnapshot;
    private String driverNameSnapshot;
    private String driverMobileSnapshot;
    private String originLocation;
    private String destinationLocation;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String waypointsJson;

    private BigDecimal estimatedDistance = BigDecimal.ZERO;
    private BigDecimal actualDistance;
    private Integer estimatedDurationMinutes;
    private Integer actualDurationMinutes;
    private OffsetDateTime expectedDeparture;
    private OffsetDateTime expectedArrival;
    private OffsetDateTime actualDeparture;
    private OffsetDateTime actualArrival;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal freightCost = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal fuelCost = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal tollCost = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal otherCharges = BigDecimal.ZERO;

    @Column(precision = 12, scale = 8)
    private BigDecimal currentLatitude;

    @Column(precision = 12, scale = 8)
    private BigDecimal currentLongitude;

    private OffsetDateTime locationUpdatedAt;

    @Column(precision = 10, scale = 2)
    private BigDecimal currentSpeed;

    @Column(precision = 10, scale = 2)
    private BigDecimal vehicleHeading;

    private String gpsProvider;

    @Column(columnDefinition = "text")
    private String remarks;
}
