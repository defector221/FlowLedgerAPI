package com.flowledger.commerce.customer.repository;

import com.flowledger.commerce.customer.entity.CommerceCustomer;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommerceCustomerRepository extends JpaRepository<CommerceCustomer, UUID> {
    Optional<CommerceCustomer> findByMobile(String mobile);

    Optional<CommerceCustomer> findByEmailIgnoreCase(String email);

    boolean existsByMobile(String mobile);

    boolean existsByEmailIgnoreCase(String email);
}
