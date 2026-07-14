package com.flowledger.customer.repository;

import com.flowledger.customer.entity.Customer;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

public interface CustomerRepository extends JpaRepository<Customer, UUID>, JpaSpecificationExecutor<Customer> {
    Optional<Customer> findByIdAndOrganizationId(UUID id, UUID organizationId);

    boolean existsByOrganizationIdAndCustomerCode(UUID organizationId, String customerCode);

    List<Customer> findByOrganizationId(UUID organizationId);

    Page<Customer> findByOrganizationId(UUID organizationId, Pageable pageable);
}
