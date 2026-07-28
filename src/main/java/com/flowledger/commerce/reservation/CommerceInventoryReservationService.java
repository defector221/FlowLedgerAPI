package com.flowledger.commerce.reservation;

import com.flowledger.commerce.cart.entity.CommerceCart;
import com.flowledger.commerce.cart.entity.CommerceCartItem;
import com.flowledger.commerce.cart.repository.CommerceCartRepository;
import com.flowledger.commerce.common.CommerceTenantScope;
import com.flowledger.commerce.events.CommerceReservationExpiredEvent;
import com.flowledger.platform.event.DomainEventPublisher;
import com.flowledger.commerce.config.CommerceProperties;
import com.flowledger.commerce.fulfillment.scan_go.repository.ScanSessionRepository;
import com.flowledger.commerce.reservation.domain.CommerceReservationStatus;
import com.flowledger.commerce.reservation.entity.CommerceInventoryReservation;
import com.flowledger.commerce.reservation.repository.CommerceInventoryReservationRepository;
import com.flowledger.common.exception.BusinessException;
import com.flowledger.inventory.entity.StockReservation;
import com.flowledger.inventory.service.StockReservationService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CommerceInventoryReservationService {
    public static final String CART_REFERENCE_TYPE = "COMMERCE_CART";
    public static final String ORDER_REFERENCE_TYPE = "COMMERCE_ORDER";
    public static final String SCAN_SESSION_REFERENCE_TYPE = "COMMERCE_SCAN_SESSION";

    private static final OffsetDateTime ORDER_LOCK_EXPIRY = OffsetDateTime.parse("2099-12-31T23:59:59Z");

    private final CommerceInventoryReservationRepository reservations;
    private final CommerceCartRepository carts;
    private final ScanSessionRepository scanSessions;
    private final StockReservationService stockReservations;
    private final CommerceProperties properties;
    private final DomainEventPublisher events;

    public CommerceInventoryReservationService(
            CommerceInventoryReservationRepository reservations,
            CommerceCartRepository carts,
            ScanSessionRepository scanSessions,
            StockReservationService stockReservations,
            CommerceProperties properties,
            DomainEventPublisher events) {
        this.reservations = reservations;
        this.carts = carts;
        this.scanSessions = scanSessions;
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
                CART_REFERENCE_TYPE,
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

    public CommerceInventoryReservation reserveForScanItem(
            UUID organizationId,
            UUID sessionId,
            UUID sessionItemId,
            UUID productId,
            UUID warehouseId,
            BigDecimal quantity) {
        OffsetDateTime expiresAt = OffsetDateTime.now()
                .plusMinutes(properties.getCart().getScanSessionReservationTtlMinutes());

        CommerceInventoryReservation existing = reservations
                .findByCartItemIdAndStatus(sessionItemId, CommerceReservationStatus.ACTIVE)
                .orElse(null);
        if (existing != null) {
            releaseReservation(organizationId, existing);
        }

        StockReservation stock = CommerceTenantScope.run(organizationId, () -> stockReservations.reserve(
                productId,
                warehouseId,
                quantity,
                null,
                null,
                SCAN_SESSION_REFERENCE_TYPE,
                sessionId,
                sessionItemId,
                expiresAt));

        CommerceInventoryReservation reservation = new CommerceInventoryReservation();
        reservation.setCartId(sessionId);
        reservation.setCartItemId(sessionItemId);
        reservation.setScanSessionId(sessionId);
        reservation.setStockReservationId(stock.getId());
        reservation.setProductId(productId);
        reservation.setWarehouseId(warehouseId);
        reservation.setQuantity(quantity);
        reservation.setStatus(CommerceReservationStatus.ACTIVE);
        reservation.setExpiresAt(expiresAt);
        return reservations.save(reservation);
    }

    public void releaseForScanSession(UUID organizationId, UUID sessionId) {
        for (CommerceInventoryReservation reservation :
                reservations.findByScanSessionIdAndStatus(sessionId, CommerceReservationStatus.ACTIVE)) {
            releaseReservation(organizationId, reservation);
        }
        CommerceTenantScope.runVoid(
                organizationId,
                () -> stockReservations.releaseByReference(SCAN_SESSION_REFERENCE_TYPE, sessionId));
    }

    public void assertCartItemsReserved(UUID cartId, List<CommerceCartItem> items) {
        for (CommerceCartItem item : items) {
            CommerceInventoryReservation reservation = reservations
                    .findByCartItemIdAndStatus(item.getId(), CommerceReservationStatus.ACTIVE)
                    .orElseThrow(() -> new BusinessException(
                            "Your cart reservation expired. Remove the item and add it again to continue."));
            if (reservation.getQuantity().compareTo(item.getQuantity()) < 0) {
                throw new BusinessException("Cart quantity changed after reservation. Refresh your cart.");
            }
        }
    }

    public void commitCartToOrder(
            UUID organizationId, UUID cartId, UUID orderId, Map<UUID, UUID> cartItemToOrderLine) {
        List<CommerceInventoryReservation> active =
                reservations.findByCartIdAndStatus(cartId, CommerceReservationStatus.ACTIVE);
        if (active.isEmpty()) {
            throw new BusinessException("No active inventory reservation for checkout. Refresh your cart.");
        }
        for (CommerceInventoryReservation reservation : active) {
            UUID orderLineId = cartItemToOrderLine.get(reservation.getCartItemId());
            if (orderLineId == null) {
                throw new BusinessException("Missing order line for reserved cart item");
            }
            CommerceTenantScope.runVoid(
                    organizationId,
                    () -> stockReservations.commitToOrder(
                            reservation.getStockReservationId(), orderId, orderLineId));
            reservation.setOrderId(orderId);
            reservation.setExpiresAt(ORDER_LOCK_EXPIRY);
            reservation.setRenewedAt(OffsetDateTime.now());
            reservations.save(reservation);
        }
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

    public void releaseForOrder(UUID organizationId, UUID orderId) {
        for (CommerceInventoryReservation reservation :
                reservations.findByOrderIdAndStatus(orderId, CommerceReservationStatus.ACTIVE)) {
            releaseReservation(organizationId, reservation);
        }
        CommerceTenantScope.runVoid(
                organizationId, () -> stockReservations.releaseByReference(ORDER_REFERENCE_TYPE, orderId));
    }

    public void renewForCart(UUID organizationId, UUID cartId) {
        OffsetDateTime newExpiry =
                OffsetDateTime.now().plusMinutes(properties.getCart().getRenewalExtensionMinutes());
        for (CommerceInventoryReservation reservation :
                reservations.findByCartIdAndStatus(cartId, CommerceReservationStatus.ACTIVE)) {
            if (reservation.getOrderId() != null) {
                continue;
            }
            reservation.setExpiresAt(newExpiry);
            reservation.setRenewedAt(OffsetDateTime.now());
            reservations.save(reservation);
        }
    }

    public int expireStale() {
        int count = 0;
        for (CommerceInventoryReservation reservation : reservations.findByStatusAndExpiresAtBeforeAndOrderIdIsNull(
                CommerceReservationStatus.ACTIVE, OffsetDateTime.now())) {
            UUID organizationId = carts.findById(reservation.getCartId())
                    .map(CommerceCart::getOrganizationId)
                    .orElseGet(() -> reservation.getScanSessionId() != null
                            ? scanSessions
                                    .findById(reservation.getScanSessionId())
                                    .map(com.flowledger.commerce.fulfillment.scan_go.entity.ScanSession::getOrganizationId)
                                    .orElse(null)
                            : null);
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
