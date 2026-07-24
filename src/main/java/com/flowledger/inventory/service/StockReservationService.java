package com.flowledger.inventory.service;

import com.flowledger.common.exception.BusinessException;
import com.flowledger.common.exception.ResourceNotFoundException;
import com.flowledger.common.tenant.TenantContext;
import com.flowledger.inventory.entity.StockReservation;
import com.flowledger.inventory.entity.StockReservation.Status;
import com.flowledger.inventory.repository.StockReservationRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockReservationService {
    private final StockReservationRepository reservations;
    private final InventoryMovementValidator movementValidator;

    public StockReservationService(
            StockReservationRepository reservations, InventoryMovementValidator movementValidator) {
        this.reservations = reservations;
        this.movementValidator = movementValidator;
    }

    @Transactional
    public StockReservation reserve(
            UUID productId,
            UUID warehouseId,
            BigDecimal qty,
            String referenceType,
            UUID referenceId,
            OffsetDateTime expiresAt) {
        if (qty == null || qty.signum() <= 0) {
            throw new BusinessException("Reservation quantity must be positive");
        }
        if (referenceType == null || referenceType.isBlank() || referenceId == null) {
            throw new BusinessException("Reservation reference is required");
        }
        UUID org = TenantContext.getOrganizationId();
        movementValidator.validateOutbound(org, productId, warehouseId, qty);

        StockReservation reservation = new StockReservation();
        reservation.setOrganizationId(org);
        reservation.setProductId(productId);
        reservation.setWarehouseId(warehouseId);
        reservation.setQty(qty);
        reservation.setReferenceType(referenceType.trim().toUpperCase());
        reservation.setReferenceId(referenceId);
        reservation.setExpiresAt(expiresAt);
        reservation.setStatus(Status.ACTIVE);
        return reservations.save(reservation);
    }

    @Transactional
    public StockReservation release(UUID reservationId) {
        StockReservation reservation = load(reservationId);
        if (reservation.getStatus() != Status.ACTIVE) {
            throw new BusinessException("Only ACTIVE reservations can be released");
        }
        reservation.setStatus(Status.RELEASED);
        return reservations.save(reservation);
    }

    @Transactional
    public void releaseByReference(String referenceType, UUID referenceId) {
        UUID org = TenantContext.getOrganizationId();
        List<StockReservation> active = reservations.findByOrganizationIdAndReferenceTypeAndReferenceIdAndStatus(
                org, referenceType.trim().toUpperCase(), referenceId, Status.ACTIVE);
        for (StockReservation reservation : active) {
            reservation.setStatus(Status.RELEASED);
            reservations.save(reservation);
        }
    }

    @Transactional
    public StockReservation consume(UUID reservationId) {
        StockReservation reservation = load(reservationId);
        if (reservation.getStatus() != Status.ACTIVE) {
            throw new BusinessException("Only ACTIVE reservations can be consumed");
        }
        reservation.setStatus(Status.CONSUMED);
        return reservations.save(reservation);
    }

    @Transactional
    public void consumeByReference(String referenceType, UUID referenceId) {
        UUID org = TenantContext.getOrganizationId();
        List<StockReservation> active = reservations.findByOrganizationIdAndReferenceTypeAndReferenceIdAndStatus(
                org, referenceType.trim().toUpperCase(), referenceId, Status.ACTIVE);
        for (StockReservation reservation : active) {
            reservation.setStatus(Status.CONSUMED);
            reservations.save(reservation);
        }
    }

    @Transactional(readOnly = true)
    public BigDecimal activeReservedQty(UUID productId, UUID warehouseId) {
        return reservations.activeReservedQty(TenantContext.getOrganizationId(), productId, warehouseId);
    }

    private StockReservation load(UUID id) {
        return reservations
                .findByIdAndOrganizationId(id, TenantContext.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Stock reservation not found"));
    }
}
