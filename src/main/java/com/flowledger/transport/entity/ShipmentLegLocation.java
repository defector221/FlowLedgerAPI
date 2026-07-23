package com.flowledger.transport.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
@Table(name = "shipment_leg_locations")
@Getter
@Setter
@NoArgsConstructor
public class ShipmentLegLocation {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID organizationId;

    @Column(nullable = false)
    private UUID legId;

    @Column(nullable = false, precision = 12, scale = 8)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 12, scale = 8)
    private BigDecimal longitude;

    @Column(precision = 10, scale = 2)
    private BigDecimal speed;

    @Column(precision = 10, scale = 2)
    private BigDecimal heading;

    @Column(nullable = false)
    private OffsetDateTime recordedAt = OffsetDateTime.now();

    private String provider;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String payloadJson;
}
