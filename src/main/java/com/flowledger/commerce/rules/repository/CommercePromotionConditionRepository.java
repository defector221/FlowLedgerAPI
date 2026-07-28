package com.flowledger.commerce.rules.repository;

import com.flowledger.commerce.rules.entity.CommercePromotionCondition;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommercePromotionConditionRepository extends JpaRepository<CommercePromotionCondition, UUID> {
    List<CommercePromotionCondition> findByRuleId(UUID ruleId);
}
