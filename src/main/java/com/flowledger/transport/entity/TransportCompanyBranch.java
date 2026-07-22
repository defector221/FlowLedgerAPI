package com.flowledger.transport.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "transport_company_branches")
@Getter
@Setter
@NoArgsConstructor
public class TransportCompanyBranch extends TransportAuditedEntity {
    @Column(nullable = false)
    private UUID companyId;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "text")
    private String address;

    private String city;
    private String state;
    private String phone;
    private String contactName;
}
