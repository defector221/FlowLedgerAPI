package com.flowledger.platform.document.entity;

import com.flowledger.common.entity.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "document_comments")
@Getter
@Setter
@NoArgsConstructor
public class DocumentComment extends AuditedEntity {
    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;
}
