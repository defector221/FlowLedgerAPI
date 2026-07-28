package com.flowledger.commerce.engagement.repository;

import com.flowledger.commerce.engagement.entity.CommerceCustomerNotification;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommerceCustomerNotificationRepository extends JpaRepository<CommerceCustomerNotification, UUID> {
    List<CommerceCustomerNotification> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);
}
