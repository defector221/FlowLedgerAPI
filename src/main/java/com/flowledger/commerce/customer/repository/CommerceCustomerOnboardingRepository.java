package com.flowledger.commerce.customer.repository;

import com.flowledger.commerce.customer.entity.CommerceCustomerOnboarding;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommerceCustomerOnboardingRepository extends JpaRepository<CommerceCustomerOnboarding, UUID> {
    Optional<CommerceCustomerOnboarding> findByCustomerId(UUID customerId);
}
