package com.flowledger.commerce.rules.repository;

import com.flowledger.commerce.rules.entity.CommercePromotionRedemption;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommercePromotionRedemptionRepository extends JpaRepository<CommercePromotionRedemption, UUID> {}
