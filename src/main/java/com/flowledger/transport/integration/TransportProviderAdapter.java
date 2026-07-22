package com.flowledger.transport.integration;

import com.flowledger.transport.entity.Shipment;

public interface TransportProviderAdapter {
    String provider();

    void shipmentDispatched(Shipment shipment);

    void shipmentDelivered(Shipment shipment);
}
