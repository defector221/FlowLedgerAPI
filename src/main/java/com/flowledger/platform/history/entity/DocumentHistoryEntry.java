package com.flowledger.platform.history.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "document_history")
@Getter
@Setter
@NoArgsConstructor
public class DocumentHistoryEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(nullable = false)
    private String summary;

    @Column(name = "detail_json", columnDefinition = "text")
    private String detailJson;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt = OffsetDateTime.now();

    @Column(name = "correlation_id")
    private UUID correlationId;
}
