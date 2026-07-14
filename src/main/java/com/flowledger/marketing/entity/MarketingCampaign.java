package com.flowledger.marketing.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowledger.common.entity.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "marketing_campaigns")
@Getter
@Setter
@NoArgsConstructor
public class MarketingCampaign extends AuditedEntity {
    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String status = "DRAFT";

    @Column(name = "audience_type", nullable = false)
    private String audienceType = "LEAD";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "filter_json", columnDefinition = "jsonb")
    private JsonNode filterJson;

    @Column(name = "email_template_id", nullable = false)
    private UUID emailTemplateId;

    @Column(name = "scheduled_at")
    private OffsetDateTime scheduledAt;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "total_count", nullable = false)
    private int totalCount;

    @Column(name = "sent_count", nullable = false)
    private int sentCount;

    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    @Column(name = "skipped_count", nullable = false)
    private int skippedCount;

    @Version
    private Long version;
}
