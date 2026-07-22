package com.flowledger.transport.entity;

import com.flowledger.transport.domain.TransportEnums.ShipmentActorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "shipment_events")
@Getter
@Setter
@NoArgsConstructor
public class ShipmentEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID shipmentId;

    @Column(nullable = false)
    private String eventType;

    @Column(nullable = false)
    private OffsetDateTime occurredAt;

    private UUID actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShipmentActorType actorType;

    @Column(columnDefinition = "text")
    private String remarks;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String locationJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String payloadJson;
}
