package com.flowledger.transport.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "transport_rate_cards")
@Getter
@Setter
@NoArgsConstructor
public class TransportRateCard extends TransportAuditedEntity {
    @Column(nullable = false)
    private UUID companyId;

    private UUID routeId;

    @Column(nullable = false)
    private String vehicleType;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal rateAmount;

    @Column(nullable = false)
    private String rateUnit;

    @Column(nullable = false)
    private String currency = "INR";

    private LocalDate validFrom;
    private LocalDate validTo;

    @Column(nullable = false)
    private boolean active = true;
}
