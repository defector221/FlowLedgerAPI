package com.flowledger.transport.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "approval_actions")
@Getter
@Setter
@NoArgsConstructor
public class ApprovalAction {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID requestId;

    @Column(nullable = false)
    private String action;

    @Column(nullable = false)
    private UUID actorId;

    @Column(nullable = false)
    private OffsetDateTime actedAt;

    @Column(columnDefinition = "text")
    private String remarks;
}
