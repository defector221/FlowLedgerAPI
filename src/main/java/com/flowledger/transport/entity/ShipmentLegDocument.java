package com.flowledger.transport.entity;

import com.flowledger.transport.domain.TransportEnums.ShipmentLegDocumentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "shipment_leg_documents")
@Getter
@Setter
@NoArgsConstructor
public class ShipmentLegDocument extends TransportAuditedEntity {
    @Column(nullable = false)
    private UUID legId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShipmentLegDocumentType documentType;

    private String fileName;
    private String storageUrl;
    private String contentType;

    @Column(columnDefinition = "text")
    private String remarks;
}
