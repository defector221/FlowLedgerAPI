package com.flowledger.transport.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "transport_companies")
@Getter
@Setter
@NoArgsConstructor
public class TransportCompany extends TransportAuditedEntity {
    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String code;

    private String gstin;
    private String pan;
    private String email;
    private String phone;

    @Column(columnDefinition = "text")
    private String address;

    private String city;
    private String state;
    private String stateCode;
    private String country = "India";

    @Column(nullable = false)
    private String status = "ACTIVE";

    @Column(columnDefinition = "text")
    private String notes;
}
