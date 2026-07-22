package com.flowledger.transport.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowledger.common.tenant.TenantContext;
import com.flowledger.transport.entity.ShipmentExternalRef;
import com.flowledger.transport.repository.ShipmentExternalRefRepository;
import com.flowledger.transport.repository.ShipmentRepository;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/transport/webhooks")
public class TransportWebhookController {
    private static final Logger log = LoggerFactory.getLogger(TransportWebhookController.class);
    private final ShipmentRepository shipments;
    private final ShipmentExternalRefRepository refs;
    private final ObjectMapper objectMapper;

    public TransportWebhookController(
            ShipmentRepository shipments, ShipmentExternalRefRepository refs, ObjectMapper objectMapper) {
        this.shipments = shipments;
        this.refs = refs;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/{provider}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void webhook(@PathVariable String provider, @RequestBody Map<String, Object> payload) {
        UUID shipmentId;
        try {
            shipmentId = UUID.fromString(String.valueOf(payload.get("shipmentId")));
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "shipmentId is required");
        }
        shipments
                .findByIdAndOrganizationIdAndDeletedFalse(shipmentId, TenantContext.getOrganizationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shipment not found"));
        String externalId = String.valueOf(payload.getOrDefault("externalId", shipmentId));
        ShipmentExternalRef ref = refs.findByShipmentIdAndProviderTypeAndExternalId(shipmentId, provider, externalId)
                .orElseGet(ShipmentExternalRef::new);
        ref.setShipmentId(shipmentId);
        ref.setProviderType(provider);
        ref.setExternalId(externalId);
        ref.setStatus(String.valueOf(payload.getOrDefault("status", "RECEIVED")));
        try {
            ref.setPayloadJson(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid webhook payload");
        }
        ref.setLastSyncedAt(OffsetDateTime.now());
        refs.save(ref);
        log.info("Stored transport webhook provider={} shipmentId={} externalId={}", provider, shipmentId, externalId);
    }
}
