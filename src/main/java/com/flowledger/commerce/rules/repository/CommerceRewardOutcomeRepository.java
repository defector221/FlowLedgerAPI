package com.flowledger.commerce.rules.repository;

import com.flowledger.commerce.rules.entity.CommerceRewardOutcome;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommerceRewardOutcomeRepository extends JpaRepository<CommerceRewardOutcome, UUID> {
    List<CommerceRewardOutcome> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);
}
