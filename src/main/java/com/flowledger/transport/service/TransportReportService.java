package com.flowledger.transport.service;

import com.flowledger.common.tenant.TenantContext;
import com.flowledger.transport.domain.TransportEnums.ShipmentStatus;
import com.flowledger.transport.entity.Shipment;
import com.flowledger.transport.entity.ShipmentLeg;
import com.flowledger.transport.repository.ShipmentLegRepository;
import com.flowledger.transport.repository.ShipmentRepository;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TransportReportService {
    public static final Set<String> REPORTS = Set.of(
            "transport-register",
            "vehicle-utilization",
            "driver-performance",
            "shipment-tracking",
            "pending-dispatch",
            "in-transit",
            "delayed-deliveries",
            "freight-cost",
            "transporter-performance",
            "vehicle-history",
            "driver-history");
    private final ShipmentRepository shipments;
    private final ShipmentLegRepository legs;

    public TransportReportService(ShipmentRepository shipments, ShipmentLegRepository legs) {
        this.shipments = shipments;
        this.legs = legs;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> report(String name) {
        if (!REPORTS.contains(name))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown transport report");
        return shipments.findAll().stream()
                .filter(shipment ->
                        shipment.getOrganizationId().equals(TenantContext.getOrganizationId()) && !shipment.isDeleted())
                .filter(shipment -> include(name, shipment))
                .flatMap(shipment -> rows(name, shipment).stream())
                .toList();
    }

    private boolean include(String name, Shipment shipment) {
        return switch (name) {
            case "pending-dispatch" ->
                EnumSet.of(
                                ShipmentStatus.APPROVED,
                                ShipmentStatus.ASSIGNED,
                                ShipmentStatus.LOADING,
                                ShipmentStatus.LOADED)
                        .contains(shipment.getStatus());
            case "in-transit" -> shipment.getStatus() == ShipmentStatus.IN_TRANSIT;
            case "delayed-deliveries" ->
                shipment.getExpectedDeliveryDate() != null
                        && shipment.getExpectedDeliveryDate().isBefore(OffsetDateTime.now())
                        && !EnumSet.of(ShipmentStatus.DELIVERED, ShipmentStatus.CLOSED, ShipmentStatus.CANCELLED)
                                .contains(shipment.getStatus());
            default -> true;
        };
    }

    private List<Map<String, Object>> rows(String name, Shipment shipment) {
        List<ShipmentLeg> shipmentLegs = legs.findByShipmentIdOrderBySequenceNo(shipment.getId());
        if (shipmentLegs.isEmpty()) return List.of(base(name, shipment, null));
        return shipmentLegs.stream().map(leg -> base(name, shipment, leg)).toList();
    }

    private Map<String, Object> base(String report, Shipment shipment, ShipmentLeg leg) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("report", report);
        row.put("shipmentId", shipment.getId());
        row.put("shipmentNumber", shipment.getShipmentNumber());
        row.put("status", shipment.getStatus());
        row.put("sourceDocumentType", shipment.getSourceDocumentType());
        row.put("sourceDocumentId", shipment.getSourceDocumentId());
        row.put("warehouseId", shipment.getFromWarehouseId());
        row.put("customerId", shipment.getShipToPartyId());
        row.put("expectedDispatchDate", shipment.getExpectedDispatchDate());
        row.put("actualDispatchDate", shipment.getActualDispatchDate());
        row.put("expectedDeliveryDate", shipment.getExpectedDeliveryDate());
        row.put("actualDeliveryDate", shipment.getActualDeliveryDate());
        row.put("freightCharges", shipment.getFreightCharges());
        row.put("transportCompanyId", leg == null ? shipment.getTransportCompanyId() : leg.getTransportCompanyId());
        row.put("vehicleId", leg == null ? null : leg.getVehicleId());
        row.put("vehicleNumber", leg == null ? null : leg.getVehicleNumberSnapshot());
        row.put("driverId", leg == null ? null : leg.getDriverId());
        row.put("driverName", leg == null ? null : leg.getDriverNameSnapshot());
        row.put("driverMobile", leg == null ? null : leg.getDriverMobileSnapshot());
        row.put("lrNumber", leg == null ? null : leg.getLrNumber());
        return row;
    }
}
