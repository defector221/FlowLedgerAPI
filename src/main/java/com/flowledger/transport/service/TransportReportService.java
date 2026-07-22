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
    public static final Set<String> REPORTS = Set.of("transport-register", "vehicle-utilization",
            "driver-performance", "shipment-tracking", "pending-dispatch", "in-transit",
            "delayed-deliveries", "freight-cost", "transporter-performance", "vehicle-history", "driver-history");
    private final ShipmentRepository shipments;
    private final ShipmentLegRepository legs;
    public TransportReportService(ShipmentRepository shipments, ShipmentLegRepository legs) { this.shipments = shipments; this.legs = legs; }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> report(String name) {
        if (!REPORTS.contains(name)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown transport report");
        return shipments.findAll().stream()
                .filter(s -> s.getOrganizationId().equals(TenantContext.getOrganizationId()) && !s.isDeleted())
                .filter(s -> include(name, s))
                .flatMap(s -> rows(name, s).stream())
                .toList();
    }

    private boolean include(String name, Shipment s) {
        return switch (name) {
            case "pending-dispatch" -> EnumSet.of(ShipmentStatus.APPROVED, ShipmentStatus.ASSIGNED,
                    ShipmentStatus.LOADING, ShipmentStatus.LOADED).contains(s.getStatus());
            case "in-transit" -> s.getStatus() == ShipmentStatus.IN_TRANSIT;
            case "delayed-deliveries" -> s.getExpectedDeliveryDate() != null
                    && s.getExpectedDeliveryDate().isBefore(OffsetDateTime.now())
                    && !EnumSet.of(ShipmentStatus.DELIVERED, ShipmentStatus.CLOSED, ShipmentStatus.CANCELLED).contains(s.getStatus());
            default -> true;
        };
    }

    private List<Map<String, Object>> rows(String name, Shipment s) {
        List<ShipmentLeg> shipmentLegs = legs.findByShipmentIdOrderBySequenceNo(s.getId());
        if (shipmentLegs.isEmpty()) return List.of(base(name, s, null));
        return shipmentLegs.stream().map(l -> base(name, s, l)).toList();
    }

    private Map<String, Object> base(String report, Shipment s, ShipmentLeg l) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("report", report); row.put("shipmentId", s.getId()); row.put("shipmentNumber", s.getShipmentNumber());
        row.put("status", s.getStatus()); row.put("sourceDocumentType", s.getSourceDocumentType());
        row.put("sourceDocumentId", s.getSourceDocumentId()); row.put("warehouseId", s.getFromWarehouseId());
        row.put("customerId", s.getShipToPartyId()); row.put("expectedDispatchDate", s.getExpectedDispatchDate());
        row.put("actualDispatchDate", s.getActualDispatchDate()); row.put("expectedDeliveryDate", s.getExpectedDeliveryDate());
        row.put("actualDeliveryDate", s.getActualDeliveryDate()); row.put("freightCharges", s.getFreightCharges());
        row.put("transportCompanyId", l == null ? s.getTransportCompanyId() : l.getTransportCompanyId());
        row.put("vehicleId", l == null ? null : l.getVehicleId()); row.put("vehicleNumber", l == null ? null : l.getVehicleNumberSnapshot());
        row.put("driverId", l == null ? null : l.getDriverId()); row.put("driverName", l == null ? null : l.getDriverNameSnapshot());
        row.put("driverMobile", l == null ? null : l.getDriverMobileSnapshot()); row.put("lrNumber", l == null ? null : l.getLrNumber());
        return row;
    }
}
