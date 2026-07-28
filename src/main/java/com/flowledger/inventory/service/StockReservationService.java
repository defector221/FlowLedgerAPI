package com.flowledger.inventory.service;

import com.flowledger.common.exception.BusinessException;
import com.flowledger.common.exception.ResourceNotFoundException;
import com.flowledger.common.tenant.TenantContext;
import com.flowledger.inventory.allocation.AllocationMode;
import com.flowledger.inventory.allocation.ReservationAvailabilityService;
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
    private final ReservationAvailabilityService availability;

    public StockReservationService(
            StockReservationRepository reservations, ReservationAvailabilityService availability) {
        this.reservations = reservations;
        this.availability = availability;
    }

    @Transactional
    public StockReservation reserve(
            UUID productId,
            UUID warehouseId,
            BigDecimal qty,
            String referenceType,
            UUID referenceId,
            OffsetDateTime expiresAt) {
        return reserve(productId, warehouseId, qty, null, null, referenceType, referenceId, null, expiresAt);
    }

    @Transactional
    public StockReservation reserve(
            UUID productId,
            UUID warehouseId,
            BigDecimal qty,
            UUID inventoryBatchId,
            AllocationMode allocationMode,
            String referenceType,
            UUID referenceId,
            UUID lineReferenceId,
            OffsetDateTime expiresAt) {
        if (qty == null || qty.signum() <= 0) {
            throw new BusinessException("Reservation quantity must be positive");
        }
        if (referenceType == null || referenceType.isBlank() || referenceId == null) {
            throw new BusinessException("Reservation reference is required");
        }
        UUID org = TenantContext.getOrganizationId();
        String refType = referenceType.trim().toUpperCase();

        if (lineReferenceId != null) {
            var existing = reservations.findByOrganizationIdAndReferenceTypeAndReferenceIdAndLineReferenceIdAndStatus(
                    org, refType, referenceId, lineReferenceId, Status.ACTIVE);
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        if (inventoryBatchId != null) {
            BigDecimal available = availability.batchAvailable(org, inventoryBatchId, null);
            if (available.compareTo(qty) < 0) {
                throw new BusinessException("Insufficient batch quantity to reserve (available="
                        + available.stripTrailingZeros().toPlainString()
                        + ")");
            }
        } else {
            BigDecimal available = availability.warehouseAvailable(org, productId, warehouseId, null);
            if (available.compareTo(qty) < 0) {
                throw new BusinessException("Insufficient stock to reserve (available="
                        + available.stripTrailingZeros().toPlainString()
                        + ")");
            }
        }

        StockReservation reservation = new StockReservation();
        reservation.setOrganizationId(org);
        reservation.setProductId(productId);
        reservation.setWarehouseId(warehouseId);
        reservation.setQty(qty);
        reservation.setInventoryBatchId(inventoryBatchId);
        reservation.setAllocationMode(allocationMode == null ? null : allocationMode.name());
        reservation.setLineReferenceId(lineReferenceId);
        reservation.setReferenceType(refType);
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
    public void commitToOrder(UUID reservationId, UUID orderId, UUID orderLineId) {
        StockReservation reservation = load(reservationId);
        if (reservation.getStatus() != Status.ACTIVE) {
            throw new BusinessException("Only ACTIVE reservations can be committed to an order");
        }
        reservation.setReferenceType("COMMERCE_ORDER");
        reservation.setReferenceId(orderId);
        reservation.setLineReferenceId(orderLineId);
        reservation.setExpiresAt(null);
        reservations.save(reservation);
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

    @Transactional
    public StockReservation splitReservation(
            UUID reservationId,
            BigDecimal splitQty,
            String newReferenceType,
            UUID newReferenceId,
            UUID newLineReferenceId) {
        StockReservation parent = load(reservationId);
        if (parent.getStatus() != Status.ACTIVE) {
            throw new BusinessException("Only ACTIVE reservations can be split");
        }
        if (splitQty == null || splitQty.signum() <= 0 || splitQty.compareTo(parent.getQty()) > 0) {
            throw new BusinessException("Invalid split quantity");
        }
        if (splitQty.compareTo(parent.getQty()) == 0) {
            parent.setReferenceType(newReferenceType.trim().toUpperCase());
            parent.setReferenceId(newReferenceId);
            parent.setLineReferenceId(newLineReferenceId);
            return reservations.save(parent);
        }
        parent.setQty(parent.getQty().subtract(splitQty));
        reservations.save(parent);

        StockReservation child = new StockReservation();
        child.setOrganizationId(parent.getOrganizationId());
        child.setProductId(parent.getProductId());
        child.setWarehouseId(parent.getWarehouseId());
        child.setQty(splitQty);
        child.setInventoryBatchId(parent.getInventoryBatchId());
        child.setAllocationMode(parent.getAllocationMode());
        child.setLineReferenceId(newLineReferenceId);
        child.setReferenceType(newReferenceType.trim().toUpperCase());
        child.setReferenceId(newReferenceId);
        child.setExpiresAt(parent.getExpiresAt());
        child.setStatus(Status.ACTIVE);
        return reservations.save(child);
    }

    @Transactional(readOnly = true)
    public BigDecimal activeReservedQty(UUID productId, UUID warehouseId) {
        return reservations.activeReservedQty(TenantContext.getOrganizationId(), productId, warehouseId);
    }

    @Transactional(readOnly = true)
    public BigDecimal activeReservedQtyByBatch(UUID batchId) {
        return reservations.activeReservedQtyByBatch(TenantContext.getOrganizationId(), batchId, null);
    }

    @Transactional(readOnly = true)
    public java.util.Optional<StockReservation> findById(UUID id) {
        return reservations.findByIdAndOrganizationId(id, TenantContext.getOrganizationId());
    }

    private StockReservation load(UUID id) {
        return reservations
                .findByIdAndOrganizationId(id, TenantContext.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Stock reservation not found"));
    }
}
