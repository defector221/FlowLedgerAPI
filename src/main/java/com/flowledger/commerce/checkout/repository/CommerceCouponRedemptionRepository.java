package com.flowledger.commerce.checkout.repository;

import com.flowledger.commerce.checkout.entity.CommerceCouponRedemption;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommerceCouponRedemptionRepository extends JpaRepository<CommerceCouponRedemption, UUID> {}
