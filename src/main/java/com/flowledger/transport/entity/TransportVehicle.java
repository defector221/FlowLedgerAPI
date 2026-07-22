package com.flowledger.transport.entity;

import com.flowledger.transport.domain.TransportEnums.VehicleOwnership;
import com.flowledger.transport.domain.TransportEnums.VehicleStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "transport_vehicles")
@Getter
@Setter
@NoArgsConstructor
public class TransportVehicle extends TransportAuditedEntity {
    private UUID companyId;

    @Column(nullable = false)
    private String vehicleNumber;

    @Column(nullable = false)
    private String vehicleType;

    private BigDecimal capacity;
    private String capacityUnit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VehicleOwnership ownership;

    private UUID driverId;
    private LocalDate fitnessExpiry;
    private LocalDate insuranceExpiry;
    private LocalDate permitExpiry;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VehicleStatus currentStatus = VehicleStatus.AVAILABLE;

    @Column(columnDefinition = "text")
    private String notes;
}
