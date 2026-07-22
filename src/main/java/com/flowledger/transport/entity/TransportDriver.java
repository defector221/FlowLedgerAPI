package com.flowledger.transport.entity;

import com.flowledger.transport.domain.TransportEnums.DriverStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "transport_drivers")
@Getter
@Setter
@NoArgsConstructor
public class TransportDriver extends TransportAuditedEntity {
    private UUID companyId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String licenseNumber;

    private LocalDate licenseExpiry;
    private String mobile;
    private String emergencyContact;
    private UUID assignedVehicleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DriverStatus currentStatus = DriverStatus.AVAILABLE;

    @Column(columnDefinition = "text")
    private String notes;
}
