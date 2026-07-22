package com.flowledger.subscription.repository;

import com.flowledger.subscription.entity.PaymentTransaction;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {
    Optional<PaymentTransaction> findByProviderOrderId(String providerOrderId);

    Optional<PaymentTransaction> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
