package com.flowledger.lead.entity;

import com.flowledger.common.entity.AuditedEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "leads")
@Getter
@Setter
@NoArgsConstructor
public class Lead extends AuditedEntity {
    @Column(name = "lead_name", nullable = false)
    private String leadName;

    private String companyName;
    private String email;
    private String phone;
    private String source;

    @Column(nullable = false)
    private String status = "NEW";

    private UUID assignedTo;

    @Column(columnDefinition = "text")
    private String notes;

    private BigDecimal estimatedValue;
    private UUID convertedCustomerId;
    private OffsetDateTime convertedAt;

    @Version
    private Long version;
}
