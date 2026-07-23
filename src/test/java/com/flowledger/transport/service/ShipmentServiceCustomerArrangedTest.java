package com.flowledger.transport.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flowledger.transport.domain.TransportEnums.TransportMode;
import com.flowledger.transport.domain.TransportEnums.TransportType;
import com.flowledger.transport.entity.Shipment;
import org.junit.jupiter.api.Test;

class ShipmentServiceCustomerArrangedTest {
    @Test
    void detectsCustomerArrangedByType() {
        Shipment shipment = new Shipment();
        shipment.setTransportType(TransportType.CUSTOMER_ARRANGED);
        shipment.setTransportMode(TransportMode.ROAD);
        assertTrue(ShipmentService.isCustomerArranged(shipment));
    }

    @Test
    void detectsCustomerPickupMode() {
        Shipment shipment = new Shipment();
        shipment.setTransportType(TransportType.THIRD_PARTY);
        shipment.setTransportMode(TransportMode.CUSTOMER_PICKUP);
        assertTrue(ShipmentService.isCustomerArranged(shipment));
    }

    @Test
    void thirdPartyRoadIsNotCustomerArranged() {
        Shipment shipment = new Shipment();
        shipment.setTransportType(TransportType.THIRD_PARTY);
        shipment.setTransportMode(TransportMode.ROAD);
        assertFalse(ShipmentService.isCustomerArranged(shipment));
    }
}
