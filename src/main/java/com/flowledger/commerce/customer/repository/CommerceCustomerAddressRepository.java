package com.flowledger.commerce.customer.repository;

import com.flowledger.commerce.customer.entity.CommerceCustomerAddress;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommerceCustomerAddressRepository extends JpaRepository<CommerceCustomerAddress, UUID> {
    List<CommerceCustomerAddress> findByCustomerIdOrderByDefaultAddressDescCreatedAtAsc(UUID customerId);

    Optional<CommerceCustomerAddress> findByIdAndCustomerId(UUID id, UUID customerId);
}
