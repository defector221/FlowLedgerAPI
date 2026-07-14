package com.flowledger.notification.repository;

import com.flowledger.notification.entity.NotificationDelivery;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, UUID> {}
