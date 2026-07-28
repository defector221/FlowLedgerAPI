package com.flowledger.commerce.reservation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowledger.commerce.cart.entity.CommerceCart;
import com.flowledger.commerce.cart.repository.CommerceCartRepository;
import com.flowledger.commerce.config.CommerceProperties;
import com.flowledger.commerce.reservation.domain.CommerceReservationStatus;
import com.flowledger.commerce.reservation.entity.CommerceInventoryReservation;
import com.flowledger.commerce.reservation.repository.CommerceInventoryReservationRepository;
import com.flowledger.inventory.service.StockReservationService;
import com.flowledger.platform.event.DomainEventPublisher;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CommerceInventoryReservationServiceTest {
    @Mock
    private CommerceInventoryReservationRepository reservations;
    @Mock
    private CommerceCartRepository carts;
    @Mock
    private StockReservationService stockReservations;
    @Mock
    private DomainEventPublisher events;
    @Mock
    private com.flowledger.commerce.fulfillment.scan_go.repository.ScanSessionRepository scanSessions;

    private CommerceInventoryReservationService service;

    @BeforeEach
    void setUp() {
        CommerceProperties properties = new CommerceProperties();
        service = new CommerceInventoryReservationService(
                reservations, carts, scanSessions, stockReservations, properties, events);
    }

    @Test
    void expireStaleReleasesStockWhenCartFound() {
        UUID cartId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID stockReservationId = UUID.randomUUID();

        CommerceInventoryReservation reservation = new CommerceInventoryReservation();
        reservation.setId(UUID.randomUUID());
        reservation.setCartId(cartId);
        reservation.setStockReservationId(stockReservationId);
        reservation.setStatus(CommerceReservationStatus.ACTIVE);
        reservation.setExpiresAt(OffsetDateTime.now().minusMinutes(1));

        CommerceCart cart = new CommerceCart();
        cart.setId(cartId);
        cart.setOrganizationId(orgId);

        when(reservations.findByStatusAndExpiresAtBeforeAndOrderIdIsNull(any(), any())).thenReturn(List.of(reservation));
        when(carts.findById(cartId)).thenReturn(Optional.of(cart));
        when(reservations.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int expired = service.expireStale();

        assertEquals(1, expired);
        verify(stockReservations).release(stockReservationId);
        verify(events).publish(any());
    }

    @Test
    void expireStaleSkipsStockReleaseWhenCartMissing() {
        CommerceInventoryReservation reservation = new CommerceInventoryReservation();
        reservation.setId(UUID.randomUUID());
        reservation.setCartId(UUID.randomUUID());
        reservation.setStatus(CommerceReservationStatus.ACTIVE);
        reservation.setExpiresAt(OffsetDateTime.now().minusMinutes(1));

        when(reservations.findByStatusAndExpiresAtBeforeAndOrderIdIsNull(any(), any())).thenReturn(List.of(reservation));
        when(carts.findById(any())).thenReturn(Optional.empty());
        when(reservations.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertEquals(1, service.expireStale());
        verify(stockReservations, never()).release(any());
    }
}
