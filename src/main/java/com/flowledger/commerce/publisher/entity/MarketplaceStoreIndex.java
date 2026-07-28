package com.flowledger.commerce.publisher.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "marketplace_store_index")
@Getter
@Setter
@NoArgsConstructor
public class MarketplaceStoreIndex {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "store_id", nullable = false, unique = true)
    private UUID storeId;

    @Column(nullable = false)
    private boolean published;

    @Column(nullable = false)
    private String visibility = "PRIVATE";

    private String name;
    private String city;

    @Column(name = "postal_code")
    private String postalCode;

    private String state;
    private String country = "IN";
    private BigDecimal latitude;
    private BigDecimal longitude;

    @Column(name = "discovery_radius_km")
    private BigDecimal discoveryRadiusKm;

    @Column(name = "search_text")
    private String searchText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload = "{}";

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = OffsetDateTime.now();
    }
}
