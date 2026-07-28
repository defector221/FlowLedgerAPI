package com.flowledger.commerce.customer.repository;

import com.flowledger.commerce.customer.entity.CommerceCustomerPreferences;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommerceCustomerPreferencesRepository extends JpaRepository<CommerceCustomerPreferences, UUID> {
    Optional<CommerceCustomerPreferences> findByCustomerId(UUID customerId);
}
