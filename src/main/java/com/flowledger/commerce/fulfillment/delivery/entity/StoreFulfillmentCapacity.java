package com.flowledger.commerce.fulfillment.delivery.entity;

import com.flowledger.commerce.common.CommerceGlobalEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "commerce_store_fulfillment_capacity")
@Getter
@Setter
@NoArgsConstructor
public class StoreFulfillmentCapacity extends CommerceGlobalEntity {
    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "store_id", nullable = false, unique = true)
    private UUID storeId;

    @Column(name = "daily_order_limit")
    private Integer dailyOrderLimit;

    @Column(name = "default_prep_minutes", nullable = false)
    private int defaultPrepMinutes = 30;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "holiday_dates", nullable = false, columnDefinition = "jsonb")
    private String holidayDates = "[]";
}
