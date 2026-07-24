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
@Table(name = "document_attachments")
@Getter
@Setter
@NoArgsConstructor
public class DocumentAttachment extends AuditedEntity {
    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;
}
