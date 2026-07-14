package com.flowledger.payment.repository;

import com.flowledger.payment.entity.PaymentReminderRule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentReminderRuleRepository extends JpaRepository<PaymentReminderRule, UUID> {
    List<PaymentReminderRule> findByOrganizationIdOrderByDaysOffsetAsc(UUID organizationId);

    List<PaymentReminderRule> findByOrganizationIdAndEnabledTrue(UUID organizationId);

    Optional<PaymentReminderRule> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
