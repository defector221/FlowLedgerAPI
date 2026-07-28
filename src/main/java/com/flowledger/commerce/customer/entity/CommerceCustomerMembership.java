package com.flowledger.commerce.customer.entity;

import com.flowledger.commerce.common.CommerceGlobalEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "commerce_customer_memberships")
@Getter
@Setter
@NoArgsConstructor
public class CommerceCustomerMembership extends CommerceGlobalEntity {
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "favorite_store_id")
    private UUID favoriteStoreId;

    @Column(nullable = false)
    private String status = "ACTIVE";

    @Column(name = "joined_at", nullable = false)
    private OffsetDateTime joinedAt = OffsetDateTime.now();
}
