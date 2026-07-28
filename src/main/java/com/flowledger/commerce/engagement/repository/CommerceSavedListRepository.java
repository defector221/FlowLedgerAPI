package com.flowledger.commerce.engagement.repository;

import com.flowledger.commerce.engagement.entity.CommerceSavedList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommerceSavedListRepository extends JpaRepository<CommerceSavedList, UUID> {
    List<CommerceSavedList> findByCustomerIdAndOrganizationIdOrderByCreatedAtDesc(UUID customerId, UUID organizationId);
}
