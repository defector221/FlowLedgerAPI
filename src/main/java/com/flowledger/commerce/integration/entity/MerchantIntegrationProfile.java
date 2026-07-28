package com.flowledger.commerce.integration.entity;

import com.flowledger.commerce.integration.domain.ConnectorType;
import com.flowledger.commerce.integration.domain.IntegrationType;
import com.flowledger.commerce.merchant.domain.MerchantType;
import com.flowledger.common.entity.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "merchant_integration_profiles")
@Getter
@Setter
@NoArgsConstructor
public class MerchantIntegrationProfile extends AuditedEntity {
    @Enumerated(EnumType.STRING)
    @Column(name = "merchant_type", nullable = false)
    private MerchantType merchantType = MerchantType.FLOWLEDGER;

    @Enumerated(EnumType.STRING)
    @Column(name = "integration_type", nullable = false)
    private IntegrationType integrationType = IntegrationType.FLOWLEDGER;

    @Enumerated(EnumType.STRING)
    @Column(name = "connector_type", nullable = false)
    private ConnectorType connectorType = ConnectorType.FLOWLEDGER;

    @Column(nullable = false)
    private String status = "INACTIVE";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "configuration_json", columnDefinition = "jsonb")
    private String configurationJson;

    @Column(name = "health_status", nullable = false)
    private String healthStatus = "UNKNOWN";

    @Column(name = "last_sync_at")
    private OffsetDateTime lastSyncAt;

    @Column(name = "last_health_check_at")
    private OffsetDateTime lastHealthCheckAt;
}
