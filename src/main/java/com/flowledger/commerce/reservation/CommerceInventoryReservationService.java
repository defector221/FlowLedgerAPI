package com.flowledger.commerce.reservation;

import com.flowledger.commerce.cart.entity.CommerceCart;
import com.flowledger.commerce.cart.entity.CommerceCartItem;
import com.flowledger.commerce.cart.repository.CommerceCartRepository;
import com.flowledger.commerce.common.CommerceTenantScope;
import com.flowledger.commerce.events.CommerceReservationExpiredEvent;
import com.flowledger.platform.event.DomainEventPublisher;
import com.flowledger.commerce.config.CommerceProperties;
import com.flowledger.commerce.reservation.domain.CommerceReservationStatus;
import com.flowledger.commerce.reservation.entity.CommerceInventoryReservation;
import com.flowledger.commerce.reservation.repository.CommerceInventoryReservationRepository;
import com.flowledger.inventory.entity.StockReservation;
import com.flowledger.inventory.service.StockReservationService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CommerceInventoryReservationService {
    public static final String REFERENCE_TYPE = "COMMERCE_CART";

    private final CommerceInventoryReservationRepository reservations;
    private final CommerceCartRepository carts;
    private final StockReservationService stockReservations;
    private final CommerceProperties properties;
    private final DomainEventPublisher events;

    public CommerceInventoryReservationService(
            CommerceInventoryReservationRepository reservations,
            CommerceCartRepository carts,
            StockReservationService stockReservations,
            CommerceProperties properties,
            DomainEventPublisher events) {
        this.reservations = reservations;
        this.carts = carts;
        this.stockReservations = stockReservations;
        this.properties = properties;
        this.events = events;
    }

    public CommerceInventoryReservation reserveForItem(
            UUID organizationId,
            UUID cartId,
            CommerceCartItem item,
            UUID warehouseId,
            BigDecimal quantity) {
        OffsetDateTime expiresAt =
                OffsetDateTime.now().plusMinutes(properties.getCart().getReservationTtlMinutes());

        CommerceInventoryReservation existing = reservations
                .findByCartItemIdAndStatus(item.getId(), CommerceReservationStatus.ACTIVE)
                .orElse(null);

        if (existing != null) {
            releaseReservation(organizationId, existing);
        }

        StockReservation stock = CommerceTenantScope.run(organizationId, () -> stockReservations.reserve(
                item.getProductId(),
                warehouseId,
                quantity,
                null,
                null,
                REFERENCE_TYPE,
                cartId,
                item.getId(),
                expiresAt));

        CommerceInventoryReservation reservation = new CommerceInventoryReservation();
        reservation.setCartId(cartId);
        reservation.setCartItemId(item.getId());
        reservation.setStockReservationId(stock.getId());
        reservation.setProductId(item.getProductId());
        reservation.setWarehouseId(warehouseId);
        reservation.setQuantity(quantity);
        reservation.setStatus(CommerceReservationStatus.ACTIVE);
        reservation.setExpiresAt(expiresAt);
        return reservations.save(reservation);
    }

    public void releaseForCartItem(UUID organizationId, UUID cartItemId) {
        reservations.findByCartItemIdAndStatus(cartItemId, CommerceReservationStatus.ACTIVE).ifPresent(r -> releaseReservation(organizationId, r));
    }

    public void releaseForCart(UUID organizationId, UUID cartId) {
        for (CommerceInventoryReservation reservation :
                reservations.findByCartIdAndStatus(cartId, CommerceReservationStatus.ACTIVE)) {
            releaseReservation(organizationId, reservation);
        }
    }

    public void renewForCart(UUID organizationId, UUID cartId) {
        OffsetDateTime newExpiry =
                OffsetDateTime.now().plusMinutes(properties.getCart().getRenewalExtensionMinutes());
        for (CommerceInventoryReservation reservation :
                reservations.findByCartIdAndStatus(cartId, CommerceReservationStatus.ACTIVE)) {
            reservation.setExpiresAt(newExpiry);
            reservation.setRenewedAt(OffsetDateTime.now());
            reservations.save(reservation);
        }
    }

    public int expireStale() {
        int count = 0;
        for (CommerceInventoryReservation reservation :
                reservations.findByStatusAndExpiresAtBefore(CommerceReservationStatus.ACTIVE, OffsetDateTime.now())) {
            UUID organizationId = carts.findById(reservation.getCartId())
                    .map(CommerceCart::getOrganizationId)
                    .orElse(null);
            if (organizationId != null) {
                releaseStock(organizationId, reservation);
                reservation.setStatus(CommerceReservationStatus.EXPIRED);
                reservations.save(reservation);
                events.publish(new CommerceReservationExpiredEvent(
                        this, organizationId, null, reservation.getId(), reservation.getCartId()));
            } else {
                reservation.setStatus(CommerceReservationStatus.EXPIRED);
                reservations.save(reservation);
            }
            count++;
        }
        return count;
    }

    private void releaseReservation(UUID organizationId, CommerceInventoryReservation reservation) {
        releaseStock(organizationId, reservation);
        reservation.setStatus(CommerceReservationStatus.RELEASED);
        reservations.save(reservation);
    }

    private void releaseStock(UUID organizationId, CommerceInventoryReservation reservation) {
        if (reservation.getStockReservationId() != null) {
            CommerceTenantScope.runVoid(
                    organizationId, () -> stockReservations.release(reservation.getStockReservationId()));
        }
    }
}
