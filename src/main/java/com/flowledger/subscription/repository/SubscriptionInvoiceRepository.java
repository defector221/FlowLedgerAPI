package com.flowledger.subscription.repository;

import com.flowledger.subscription.entity.SubscriptionInvoice;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionInvoiceRepository extends JpaRepository<SubscriptionInvoice, UUID> {
    List<SubscriptionInvoice> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);
}
