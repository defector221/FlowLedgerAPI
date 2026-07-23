package com.flowledger.retail.entity;

import com.flowledger.retail.domain.RetailEnums.CountType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "retail_stock_counts")
@Getter
@Setter
@NoArgsConstructor
public class RetailStockCount extends RetailAuditedEntity {
    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "location_id")
    private UUID locationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "count_type", nullable = false)
    private CountType countType = CountType.CYCLE;

    @Column(nullable = false)
    private String status = "DRAFT";

    @Column(name = "counted_at")
    private OffsetDateTime countedAt;

    @Column(columnDefinition = "text")
    private String notes;
}
