package com.flowledger.transport.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "transport_service_areas")
@Getter
@Setter
@NoArgsConstructor
public class TransportServiceArea extends TransportAuditedEntity {
    @Column(nullable = false)
    private UUID companyId;

    private String stateCode;
    private String stateName;
    private String city;

    @Column(columnDefinition = "text")
    private String notes;
}
