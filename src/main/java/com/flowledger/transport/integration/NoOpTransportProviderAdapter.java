package com.flowledger.transport.integration;

import com.flowledger.transport.entity.Shipment;
import org.springframework.stereotype.Component;

@Component
public class NoOpTransportProviderAdapter implements TransportProviderAdapter {
    @Override public String provider() { return "noop"; }
    @Override public void shipmentDispatched(Shipment shipment) {}
    @Override public void shipmentDelivered(Shipment shipment) {}
}
