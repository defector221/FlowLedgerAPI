package com.flowledger.lead.entity;

import com.flowledger.common.entity.AuditedEntity;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "lead_follow_ups")
@Getter
@Setter
@NoArgsConstructor
public class LeadFollowUp extends AuditedEntity {
    @Column(name = "lead_id", nullable = false)
    private UUID leadId;

    @Column(name = "follow_up_at", nullable = false)
    private OffsetDateTime followUpAt;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(nullable = false)
    private String status = "PENDING";

    private OffsetDateTime completedAt;
}
