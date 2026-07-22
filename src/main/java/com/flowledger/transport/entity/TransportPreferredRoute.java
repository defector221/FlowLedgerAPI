package com.flowledger.transport.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "transport_preferred_routes")
@Getter
@Setter
@NoArgsConstructor
public class TransportPreferredRoute extends TransportAuditedEntity {
    @Column(nullable = false)
    private UUID companyId;

    @Column(nullable = false)
    private String fromLocation;

    @Column(nullable = false)
    private String toLocation;

    private String via;
    private BigDecimal distanceKm;

    @Column(columnDefinition = "text")
    private String notes;
}
