package com.flowledger.transport.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "transport_company_contacts")
@Getter
@Setter
@NoArgsConstructor
public class TransportCompanyContact extends TransportAuditedEntity {
    @Column(nullable = false)
    private UUID companyId;

    @Column(nullable = false)
    private String name;

    private String role;
    private String mobile;
    private String email;

    @Column(nullable = false)
    private boolean primaryContact;
}
