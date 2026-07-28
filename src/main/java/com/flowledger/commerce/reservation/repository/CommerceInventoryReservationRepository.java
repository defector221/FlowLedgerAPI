package com.flowledger.commerce.reservation.repository;

import com.flowledger.commerce.reservation.domain.CommerceReservationStatus;
import com.flowledger.commerce.reservation.entity.CommerceInventoryReservation;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommerceInventoryReservationRepository extends JpaRepository<CommerceInventoryReservation, UUID> {
    Optional<CommerceInventoryReservation> findByCartItemIdAndStatus(UUID cartItemId, CommerceReservationStatus status);

    List<CommerceInventoryReservation> findByCartIdAndStatus(UUID cartId, CommerceReservationStatus status);

    List<CommerceInventoryReservation> findByOrderIdAndStatus(UUID orderId, CommerceReservationStatus status);

    List<CommerceInventoryReservation> findByScanSessionIdAndStatus(UUID scanSessionId, CommerceReservationStatus status);

    List<CommerceInventoryReservation> findByStatusAndExpiresAtBeforeAndOrderIdIsNull(
            CommerceReservationStatus status, OffsetDateTime expiresAt);
}
