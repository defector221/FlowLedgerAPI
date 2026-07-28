package com.flowledger.commerce.analytics.repository;

import com.flowledger.commerce.analytics.entity.AnalyticsPromotionFact;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalyticsPromotionFactRepository extends JpaRepository<AnalyticsPromotionFact, UUID> {}
