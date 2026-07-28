package com.flowledger.commerce.fulfillment.delivery.repository;

import com.flowledger.commerce.fulfillment.delivery.entity.SlotBooking;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SlotBookingRepository extends JpaRepository<SlotBooking, UUID> {}
