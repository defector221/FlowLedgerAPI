package com.flowledger.ai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ai_embeddings")
@Getter
@Setter
@NoArgsConstructor
public class AiEmbedding {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "source_type", nullable = false)
    private String sourceType;

    @Column(name = "source_id", nullable = false)
    private UUID sourceId;

    @Column(name = "content_hash")
    private String contentHash;

    @Column(name = "embedding_json", columnDefinition = "text")
    private String embeddingJson;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}
