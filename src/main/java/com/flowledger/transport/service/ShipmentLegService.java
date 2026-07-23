package com.flowledger.transport.service;

import static com.flowledger.transport.dto.TransportDtos.*;

import com.flowledger.common.tenant.TenantContext;
import com.flowledger.transport.domain.TransportEnums.ShipmentActorType;
import com.flowledger.transport.domain.TransportEnums.ShipmentLegStatus;
import com.flowledger.transport.domain.TransportEnums.ShipmentStatus;
import com.flowledger.transport.entity.Shipment;
import com.flowledger.transport.entity.ShipmentEvent;
import com.flowledger.transport.entity.ShipmentLeg;
import com.flowledger.transport.entity.ShipmentLegDocument;
import com.flowledger.transport.entity.ShipmentLegLocation;
import com.flowledger.transport.repository.ShipmentEventRepository;
import com.flowledger.transport.repository.ShipmentLegDocumentRepository;
import com.flowledger.transport.repository.ShipmentLegLocationRepository;
import com.flowledger.transport.repository.ShipmentLegRepository;
import com.flowledger.transport.repository.ShipmentRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class ShipmentLegService {
    private static final EnumSet<ShipmentLegStatus> ACTIVE = EnumSet.of(
            ShipmentLegStatus.READY,
            ShipmentLegStatus.DISPATCHED,
            ShipmentLegStatus.IN_TRANSIT,
            ShipmentLegStatus.ARRIVED);

    private final ShipmentRepository shipments;
    private final ShipmentLegRepository legs;
    private final ShipmentEventRepository events;
    private final ShipmentLegLocationRepository locations;
    private final ShipmentLegDocumentRepository documents;
    private final IntegrationOutboxService outbox;
    private final TransportActivityNotificationService activityNotifications;
    private final boolean requireSequentialDispatch;
    private final boolean enforceDriverOverlap;
    private final boolean enforceVehicleOverlap;

    public ShipmentLegService(
            ShipmentRepository shipments,
            ShipmentLegRepository legs,
            ShipmentEventRepository events,
            ShipmentLegLocationRepository locations,
            ShipmentLegDocumentRepository documents,
            IntegrationOutboxService outbox,
            TransportActivityNotificationService activityNotifications,
            @Value("${flowledger.transport.legs.require-sequential-dispatch:true}") boolean requireSequentialDispatch,
            @Value("${flowledger.transport.legs.enforce-driver-overlap:true}") boolean enforceDriverOverlap,
            @Value("${flowledger.transport.legs.enforce-vehicle-overlap:true}") boolean enforceVehicleOverlap) {
        this.shipments = shipments;
        this.legs = legs;
        this.events = events;
        this.locations = locations;
        this.documents = documents;
        this.outbox = outbox;
        this.activityNotifications = activityNotifications;
        this.requireSequentialDispatch = requireSequentialDispatch;
        this.enforceDriverOverlap = enforceDriverOverlap;
        this.enforceVehicleOverlap = enforceVehicleOverlap;
    }

    @Transactional(readOnly = true)
    public List<LegResponse> list(UUID shipmentId) {
        loadShipment(shipmentId);
        return legs.findByShipmentIdAndDeletedFalseOrderBySequenceNo(shipmentId).stream()
                .map(this::mapLeg)
                .toList();
    }

    public LegResponse add(UUID shipmentId, LegRequest request) {
        Shipment shipment = loadShipment(shipmentId);
        if (isTerminal(shipment.getStatus())) {
            conflict("Cannot add legs to a closed shipment");
        }
        int sequence = request.sequenceNo() == null
                ? nextSequence(shipmentId)
                : request.sequenceNo();
        if (legs.existsByShipmentIdAndSequenceNoAndDeletedFalse(shipmentId, sequence)) {
            conflict("Leg sequence " + sequence + " already exists");
        }
        ShipmentLeg leg = newLeg(shipment, sequence);
        apply(leg, request);
        validateChain(shipmentId, leg, true);
        validateOverlap(leg, null);
        validateTimes(leg);
        audit(leg, true);
        leg = legs.save(leg);
        timeline(shipment, "LEG_CREATED", "Leg " + sequence + " planned", null);
        outbox.enqueue("LegCreated", "SHIPMENT_LEG", leg.getId(), "{\"shipmentId\":\"" + shipmentId + "\"}");
        recompute(shipment);
        return mapLeg(leg);
    }

    public LegResponse update(UUID legId, LegRequest request) {
        ShipmentLeg leg = loadLeg(legId);
        Shipment shipment = loadShipment(leg.getShipmentId());
        if (leg.getStatus() == ShipmentLegStatus.COMPLETED || leg.getStatus() == ShipmentLegStatus.CANCELLED) {
            conflict("Completed or cancelled legs cannot be edited");
        }
        Integer sequence = request.sequenceNo() == null ? leg.getSequenceNo() : request.sequenceNo();
        if (!Objects.equals(sequence, leg.getSequenceNo())
                && legs.existsByShipmentIdAndSequenceNoAndDeletedFalseAndIdNot(
                        leg.getShipmentId(), sequence, leg.getId())) {
            conflict("Leg sequence " + sequence + " already exists");
        }
        leg.setSequenceNo(sequence);
        applyPatch(leg, request);
        validateChain(leg.getShipmentId(), leg, true);
        validateOverlap(leg, leg.getId());
        validateTimes(leg);
        audit(leg, false);
        leg = legs.save(leg);
        timeline(shipment, "LEG_UPDATED", "Leg " + leg.getSequenceNo() + " updated", request.remarks());
        recompute(shipment);
        return mapLeg(leg);
    }

    public void delete(UUID legId) {
        ShipmentLeg leg = loadLeg(legId);
        Shipment shipment = loadShipment(leg.getShipmentId());
        if (leg.getStatus() == ShipmentLegStatus.DISPATCHED
                || leg.getStatus() == ShipmentLegStatus.IN_TRANSIT
                || leg.getStatus() == ShipmentLegStatus.ARRIVED
                || leg.getStatus() == ShipmentLegStatus.COMPLETED) {
            conflict("Cannot delete an active or completed leg");
        }
        leg.setDeleted(true);
        audit(leg, false);
        legs.save(leg);
        timeline(shipment, "LEG_CANCELLED", "Leg " + leg.getSequenceNo() + " removed", null);
        outbox.enqueue("LegCancelled", "SHIPMENT_LEG", leg.getId(), "{\"shipmentId\":\"" + shipment.getId() + "\"}");
        recompute(shipment);
    }

    public LegResponse setStatus(UUID legId, LegStatusRequest request) {
        ShipmentLeg leg = loadLeg(legId);
        Shipment shipment = loadShipment(leg.getShipmentId());
        ShipmentLegStatus target = request.status();
        if (target == ShipmentLegStatus.DISPATCHED) return dispatch(legId, new TransitionRequest(request.remarks(), null, null));
        if (target == ShipmentLegStatus.ARRIVED) return arrive(legId, new TransitionRequest(request.remarks(), null, null));
        if (target == ShipmentLegStatus.COMPLETED) return complete(legId, new TransitionRequest(request.remarks(), null, null));
        if (target == ShipmentLegStatus.CANCELLED) {
            leg.setStatus(ShipmentLegStatus.CANCELLED);
            audit(leg, false);
            legs.save(leg);
            timeline(shipment, "LEG_CANCELLED", request.remarks(), null);
            outbox.enqueue("LegCancelled", "SHIPMENT_LEG", leg.getId(), "{\"shipmentId\":\"" + shipment.getId() + "\"}");
            recompute(shipment);
            return mapLeg(leg);
        }
        leg.setStatus(target);
        audit(leg, false);
        legs.save(leg);
        timeline(shipment, "LEG_" + target.name(), request.remarks(), null);
        recompute(shipment);
        return mapLeg(leg);
    }

    public LegResponse dispatch(UUID legId, TransitionRequest request) {
        ShipmentLeg leg = loadLeg(legId);
        Shipment shipment = loadShipment(leg.getShipmentId());
        assertPriorCompleted(leg);
        if (leg.getStatus() != ShipmentLegStatus.PLANNED && leg.getStatus() != ShipmentLegStatus.READY) {
            conflict("Leg must be PLANNED or READY to dispatch");
        }
        validateOverlap(leg, leg.getId());
        leg.setStatus(ShipmentLegStatus.DISPATCHED);
        leg.setActualDeparture(OffsetDateTime.now());
        audit(leg, false);
        legs.save(leg);
        timeline(shipment, "LEG_DISPATCHED", remarks(request), null);
        outbox.enqueue("LegDispatched", "SHIPMENT_LEG", leg.getId(), "{\"shipmentId\":\"" + shipment.getId() + "\"}");
        recompute(shipment);
        return mapLeg(leg);
    }

    public LegResponse arrive(UUID legId, TransitionRequest request) {
        ShipmentLeg leg = loadLeg(legId);
        Shipment shipment = loadShipment(leg.getShipmentId());
        if (leg.getStatus() != ShipmentLegStatus.DISPATCHED && leg.getStatus() != ShipmentLegStatus.IN_TRANSIT) {
            conflict("Leg must be DISPATCHED or IN_TRANSIT to arrive");
        }
        OffsetDateTime arrival = OffsetDateTime.now();
        if (leg.getActualDeparture() != null && arrival.isBefore(leg.getActualDeparture())) {
            conflict("Arrival time cannot be before departure");
        }
        leg.setStatus(ShipmentLegStatus.ARRIVED);
        leg.setActualArrival(arrival);
        audit(leg, false);
        legs.save(leg);
        timeline(shipment, "LEG_ARRIVED", remarks(request), null);
        outbox.enqueue("LegArrived", "SHIPMENT_LEG", leg.getId(), "{\"shipmentId\":\"" + shipment.getId() + "\"}");
        recompute(shipment);
        return mapLeg(leg);
    }

    public LegResponse complete(UUID legId, TransitionRequest request) {
        ShipmentLeg leg = loadLeg(legId);
        Shipment shipment = loadShipment(leg.getShipmentId());
        if (leg.getStatus() != ShipmentLegStatus.ARRIVED
                && leg.getStatus() != ShipmentLegStatus.IN_TRANSIT
                && leg.getStatus() != ShipmentLegStatus.DISPATCHED) {
            conflict("Leg is not ready to complete");
        }
        if (leg.getActualArrival() == null) leg.setActualArrival(OffsetDateTime.now());
        leg.setStatus(ShipmentLegStatus.COMPLETED);
        audit(leg, false);
        legs.save(leg);
        timeline(shipment, "LEG_COMPLETED", remarks(request), null);
        outbox.enqueue("LegCompleted", "SHIPMENT_LEG", leg.getId(), "{\"shipmentId\":\"" + shipment.getId() + "\"}");
        recompute(shipment);
        return mapLeg(leg);
    }

    public LegResponse updateLocation(UUID legId, LegLocationRequest request) {
        ShipmentLeg leg = loadLeg(legId);
        Shipment shipment = loadShipment(leg.getShipmentId());
        leg.setCurrentLatitude(request.latitude());
        leg.setCurrentLongitude(request.longitude());
        leg.setCurrentSpeed(request.speed());
        leg.setVehicleHeading(request.heading());
        leg.setGpsProvider(request.provider());
        leg.setLocationUpdatedAt(OffsetDateTime.now());
        if (leg.getStatus() == ShipmentLegStatus.DISPATCHED) {
            leg.setStatus(ShipmentLegStatus.IN_TRANSIT);
        }
        audit(leg, false);
        legs.save(leg);

        ShipmentLegLocation point = new ShipmentLegLocation();
        point.setOrganizationId(org());
        point.setLegId(leg.getId());
        point.setLatitude(request.latitude());
        point.setLongitude(request.longitude());
        point.setSpeed(request.speed());
        point.setHeading(request.heading());
        point.setProvider(request.provider());
        point.setPayloadJson(request.payloadJson());
        point.setRecordedAt(OffsetDateTime.now());
        locations.save(point);

        String locationJson = "{\"lat\":" + request.latitude() + ",\"lng\":" + request.longitude() + "}";
        timeline(shipment, "GPS_UPDATED", request.remarks(), locationJson);
        recompute(shipment);
        return mapLeg(leg);
    }

    @Transactional(readOnly = true)
    public List<LegLocationResponse> locationHistory(UUID legId) {
        loadLeg(legId);
        return locations.findByLegIdOrderByRecordedAtDesc(legId).stream()
                .map(p -> new LegLocationResponse(
                        p.getId(),
                        p.getLegId(),
                        p.getLatitude(),
                        p.getLongitude(),
                        p.getSpeed(),
                        p.getHeading(),
                        p.getRecordedAt(),
                        p.getProvider()))
                .toList();
    }

    public LegDocumentResponse addDocument(UUID legId, LegDocumentRequest request) {
        ShipmentLeg leg = loadLeg(legId);
        Shipment shipment = loadShipment(leg.getShipmentId());
        ShipmentLegDocument doc = new ShipmentLegDocument();
        doc.setOrganizationId(org());
        doc.setLegId(legId);
        doc.setDocumentType(request.documentType());
        doc.setFileName(request.fileName());
        doc.setStorageUrl(request.storageUrl());
        doc.setContentType(request.contentType());
        doc.setRemarks(request.remarks());
        doc.setDeleted(false);
        UUID actor = TenantContext.userId().orElse(null);
        if (actor != null) {
            doc.setCreatedBy(actor);
            doc.setUpdatedBy(actor);
        }
        ShipmentLegDocument saved = documents.save(doc);
        String type = request.documentType() == null ? "DOCUMENT" : request.documentType().name();
        timeline(
                shipment,
                type.equals("POD") ? "POD_UPLOADED" : "LEG_DOCUMENT_UPLOADED",
                request.fileName() == null ? type : request.fileName(),
                null);
        return mapDoc(saved);
    }

    @Transactional(readOnly = true)
    public List<LegDocumentResponse> listDocuments(UUID legId) {
        loadLeg(legId);
        return documents.findByLegIdAndDeletedFalseOrderByCreatedAtDesc(legId).stream()
                .map(this::mapDoc)
                .toList();
    }

    public void recompute(Shipment shipment) {
        List<ShipmentLeg> active =
                legs.findByShipmentIdAndDeletedFalseOrderBySequenceNo(shipment.getId()).stream()
                        .filter(l -> l.getStatus() != ShipmentLegStatus.CANCELLED)
                        .toList();
        BigDecimal freight = BigDecimal.ZERO;
        BigDecimal fuel = BigDecimal.ZERO;
        BigDecimal toll = BigDecimal.ZERO;
        BigDecimal other = BigDecimal.ZERO;
        BigDecimal distance = BigDecimal.ZERO;
        for (ShipmentLeg leg : active) {
            freight = freight.add(z(leg.getFreightCost()));
            fuel = fuel.add(z(leg.getFuelCost()));
            toll = toll.add(z(leg.getTollCost()));
            other = other.add(z(leg.getOtherCharges()));
            if (leg.getActualDistance() != null) distance = distance.add(leg.getActualDistance());
            else if (leg.getEstimatedDistance() != null) distance = distance.add(leg.getEstimatedDistance());
        }
        shipment.setFreightCharges(freight);
        shipment.setFuelChargesTotal(fuel);
        shipment.setTollChargesTotal(toll);
        shipment.setOtherChargesTotal(other);
        shipment.setGrandTotal(freight.add(fuel).add(toll).add(other));
        shipment.setTotalDistance(distance);

        if (!isTerminal(shipment.getStatus()) && shipment.getStatus() != ShipmentStatus.REJECTED) {
            ShipmentStatus rolled = rollupStatus(shipment.getStatus(), active);
            if (rolled != null && rolled != shipment.getStatus()) {
                shipment.setStatus(rolled);
                if (rolled == ShipmentStatus.DELIVERED && shipment.getActualDeliveryDate() == null) {
                    shipment.setActualDeliveryDate(OffsetDateTime.now());
                }
                if ((rolled == ShipmentStatus.DISPATCHED || rolled == ShipmentStatus.IN_TRANSIT)
                        && shipment.getActualDispatchDate() == null) {
                    shipment.setActualDispatchDate(OffsetDateTime.now());
                }
                timeline(shipment, rolled.name(), "Status rolled up from legs", null);
                if (rolled == ShipmentStatus.DELIVERED) {
                    outbox.enqueue(
                            "ShipmentCompleted",
                            "SHIPMENT",
                            shipment.getId(),
                            "{\"shipmentNumber\":\"" + shipment.getShipmentNumber() + "\"}");
                }
            }
        }
        TenantContext.userId().ifPresent(shipment::setUpdatedBy);
        shipments.save(shipment);
    }

    private ShipmentStatus rollupStatus(ShipmentStatus current, List<ShipmentLeg> active) {
        if (active.isEmpty()) return null;
        boolean allCompleted = active.stream().allMatch(l -> l.getStatus() == ShipmentLegStatus.COMPLETED);
        if (allCompleted) return ShipmentStatus.DELIVERED;
        boolean anyMoving = active.stream()
                .anyMatch(l -> l.getStatus() == ShipmentLegStatus.DISPATCHED
                        || l.getStatus() == ShipmentLegStatus.IN_TRANSIT
                        || l.getStatus() == ShipmentLegStatus.ARRIVED);
        boolean anyCompleted = active.stream().anyMatch(l -> l.getStatus() == ShipmentLegStatus.COMPLETED);
        if (anyMoving || anyCompleted) {
            if (anyCompleted && !allCompleted) {
                return current == ShipmentStatus.PARTIALLY_DISPATCHED
                        ? ShipmentStatus.PARTIALLY_DISPATCHED
                        : ShipmentStatus.IN_TRANSIT;
            }
            return ShipmentStatus.IN_TRANSIT;
        }
        return null;
    }

    private void assertPriorCompleted(ShipmentLeg leg) {
        if (!requireSequentialDispatch) return;
        List<ShipmentLeg> all = legs.findByShipmentIdAndDeletedFalseOrderBySequenceNo(leg.getShipmentId());
        for (ShipmentLeg prior : all) {
            if (prior.getSequenceNo() >= leg.getSequenceNo()) break;
            if (prior.getStatus() == ShipmentLegStatus.CANCELLED) continue;
            if (prior.getStatus() != ShipmentLegStatus.COMPLETED) {
                conflict("Complete leg " + prior.getSequenceNo() + " before dispatching leg " + leg.getSequenceNo());
            }
        }
    }

    private void validateChain(UUID shipmentId, ShipmentLeg candidate, boolean strict) {
        List<ShipmentLeg> all = legs.findByShipmentIdAndDeletedFalseOrderBySequenceNo(shipmentId).stream()
                .filter(l -> !Objects.equals(l.getId(), candidate.getId()))
                .toList();
        // include candidate sorted
        java.util.ArrayList<ShipmentLeg> merged = new java.util.ArrayList<>(all);
        merged.add(candidate);
        merged.sort(java.util.Comparator.comparingInt(ShipmentLeg::getSequenceNo));
        for (int i = 1; i < merged.size(); i++) {
            ShipmentLeg prev = merged.get(i - 1);
            ShipmentLeg next = merged.get(i);
            if (prev.getStatus() == ShipmentLegStatus.CANCELLED || next.getStatus() == ShipmentLegStatus.CANCELLED) {
                continue;
            }
            String dest = normalize(prev.getDestinationLocation());
            String origin = normalize(next.getOriginLocation());
            if (dest == null || origin == null) continue;
            if (!dest.equals(origin)) {
                if (strict) {
                    conflict("Leg " + next.getSequenceNo() + " origin must match leg " + prev.getSequenceNo()
                            + " destination");
                }
            }
        }
    }

    private void validateOverlap(ShipmentLeg leg, UUID excludeId) {
        if (leg.getDriverId() != null && enforceDriverOverlap) {
            boolean busy = legs
                    .findByOrganizationIdAndDriverIdAndDeletedFalseAndStatusIn(org(), leg.getDriverId(), List.copyOf(ACTIVE))
                    .stream()
                    .anyMatch(other -> !Objects.equals(other.getId(), excludeId));
            if (busy) conflict("Driver is already assigned to an active leg");
        }
        if (leg.getVehicleId() != null && enforceVehicleOverlap) {
            boolean busy = legs
                    .findByOrganizationIdAndVehicleIdAndDeletedFalseAndStatusIn(
                            org(), leg.getVehicleId(), List.copyOf(ACTIVE))
                    .stream()
                    .anyMatch(other -> !Objects.equals(other.getId(), excludeId));
            if (busy) conflict("Vehicle is already assigned to an active leg");
        }
    }

    private void validateTimes(ShipmentLeg leg) {
        if (leg.getExpectedDeparture() != null
                && leg.getExpectedArrival() != null
                && leg.getExpectedArrival().isBefore(leg.getExpectedDeparture())) {
            conflict("Arrival time cannot be before departure");
        }
        if (leg.getActualDeparture() != null
                && leg.getActualArrival() != null
                && leg.getActualArrival().isBefore(leg.getActualDeparture())) {
            conflict("Arrival time cannot be before departure");
        }
    }

    private ShipmentLeg newLeg(Shipment shipment, int sequence) {
        ShipmentLeg leg = new ShipmentLeg();
        leg.setOrganizationId(shipment.getOrganizationId());
        leg.setShipmentId(shipment.getId());
        leg.setSequenceNo(sequence);
        leg.setStatus(ShipmentLegStatus.PLANNED);
        leg.setTransportMode(shipment.getTransportMode());
        leg.setDeleted(false);
        return leg;
    }

    private void apply(ShipmentLeg leg, LegRequest request) {
        if (request == null) return;
        leg.setTransportCompanyId(request.transportCompanyId());
        leg.setVehicleId(request.vehicleId());
        leg.setDriverId(request.driverId());
        leg.setLrNumber(request.lrNumber());
        leg.setConsignmentNumber(request.consignmentNumber());
        leg.setVehicleNumberSnapshot(request.vehicleNumberSnapshot());
        leg.setDriverNameSnapshot(request.driverNameSnapshot());
        leg.setDriverMobileSnapshot(request.driverMobileSnapshot());
        leg.setExpectedDeparture(request.expectedDeparture());
        leg.setExpectedArrival(request.expectedArrival());
        leg.setRemarks(request.remarks());
        if (request.transportMode() != null) leg.setTransportMode(request.transportMode());
        leg.setOriginLocation(request.originLocation());
        leg.setDestinationLocation(request.destinationLocation());
        leg.setWaypointsJson(request.waypointsJson());
        if (request.estimatedDistance() != null) leg.setEstimatedDistance(request.estimatedDistance());
        if (request.estimatedDurationMinutes() != null) leg.setEstimatedDurationMinutes(request.estimatedDurationMinutes());
        if (request.freightCost() != null) leg.setFreightCost(request.freightCost());
        if (request.fuelCost() != null) leg.setFuelCost(request.fuelCost());
        if (request.tollCost() != null) leg.setTollCost(request.tollCost());
        if (request.otherCharges() != null) leg.setOtherCharges(request.otherCharges());
    }

    /** Partial update — only overwrite fields explicitly provided by the client. */
    private void applyPatch(ShipmentLeg leg, LegRequest request) {
        if (request == null) return;
        if (request.transportCompanyId() != null) leg.setTransportCompanyId(request.transportCompanyId());
        if (request.vehicleId() != null) leg.setVehicleId(request.vehicleId());
        if (request.driverId() != null) leg.setDriverId(request.driverId());
        if (request.lrNumber() != null) leg.setLrNumber(blankToNull(request.lrNumber()));
        if (request.consignmentNumber() != null) leg.setConsignmentNumber(blankToNull(request.consignmentNumber()));
        if (request.vehicleNumberSnapshot() != null)
            leg.setVehicleNumberSnapshot(blankToNull(request.vehicleNumberSnapshot()));
        if (request.driverNameSnapshot() != null) leg.setDriverNameSnapshot(blankToNull(request.driverNameSnapshot()));
        if (request.driverMobileSnapshot() != null)
            leg.setDriverMobileSnapshot(blankToNull(request.driverMobileSnapshot()));
        if (request.expectedDeparture() != null) leg.setExpectedDeparture(request.expectedDeparture());
        if (request.expectedArrival() != null) leg.setExpectedArrival(request.expectedArrival());
        if (request.remarks() != null) leg.setRemarks(blankToNull(request.remarks()));
        if (request.transportMode() != null) leg.setTransportMode(request.transportMode());
        if (request.originLocation() != null) leg.setOriginLocation(blankToNull(request.originLocation()));
        if (request.destinationLocation() != null)
            leg.setDestinationLocation(blankToNull(request.destinationLocation()));
        if (request.waypointsJson() != null) leg.setWaypointsJson(blankToNull(request.waypointsJson()));
        if (request.estimatedDistance() != null) leg.setEstimatedDistance(request.estimatedDistance());
        if (request.estimatedDurationMinutes() != null)
            leg.setEstimatedDurationMinutes(request.estimatedDurationMinutes());
        if (request.freightCost() != null) leg.setFreightCost(request.freightCost());
        if (request.fuelCost() != null) leg.setFuelCost(request.fuelCost());
        if (request.tollCost() != null) leg.setTollCost(request.tollCost());
        if (request.otherCharges() != null) leg.setOtherCharges(request.otherCharges());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private int nextSequence(UUID shipmentId) {
        return legs.findByShipmentIdAndDeletedFalseOrderBySequenceNo(shipmentId).stream()
                        .mapToInt(ShipmentLeg::getSequenceNo)
                        .max()
                        .orElse(0)
                + 1;
    }

    private Shipment loadShipment(UUID id) {
        return shipments
                .findByIdAndOrganizationIdAndDeletedFalse(id, org())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shipment not found"));
    }

    private ShipmentLeg loadLeg(UUID id) {
        return legs.findByIdAndOrganizationIdAndDeletedFalse(id, org())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shipment leg not found"));
    }

    private void timeline(Shipment shipment, String type, String remarks, String locationJson) {
        ShipmentEvent event = new ShipmentEvent();
        event.setShipmentId(shipment.getId());
        event.setEventType(type);
        event.setOccurredAt(OffsetDateTime.now());
        event.setActorUserId(TenantContext.userId().orElse(null));
        event.setActorType(TenantContext.userId().isPresent() ? ShipmentActorType.USER : ShipmentActorType.SYSTEM);
        event.setRemarks(remarks);
        event.setLocationJson(locationJson);
        events.save(event);
        activityNotifications.notifyShipmentEvent(shipment, type, remarks);
    }

    private void audit(ShipmentLeg leg, boolean created) {
        TenantContext.userId().ifPresent(u -> {
            if (created) leg.setCreatedBy(u);
            leg.setUpdatedBy(u);
        });
    }

    LegResponse mapLeg(ShipmentLeg leg) {
        return new LegResponse(
                leg.getId(),
                leg.getSequenceNo(),
                leg.getTransportCompanyId(),
                leg.getVehicleId(),
                leg.getDriverId(),
                leg.getLrNumber(),
                leg.getConsignmentNumber(),
                leg.getVehicleNumberSnapshot(),
                leg.getDriverNameSnapshot(),
                leg.getDriverMobileSnapshot(),
                leg.getExpectedDeparture(),
                leg.getExpectedArrival(),
                leg.getActualDeparture(),
                leg.getActualArrival(),
                leg.getRemarks(),
                leg.getStatus(),
                leg.getTransportMode(),
                leg.getOriginLocation(),
                leg.getDestinationLocation(),
                leg.getWaypointsJson(),
                leg.getEstimatedDistance(),
                leg.getActualDistance(),
                leg.getEstimatedDurationMinutes(),
                leg.getActualDurationMinutes(),
                leg.getFreightCost(),
                leg.getFuelCost(),
                leg.getTollCost(),
                leg.getOtherCharges(),
                leg.getCurrentLatitude(),
                leg.getCurrentLongitude(),
                leg.getLocationUpdatedAt(),
                leg.getCurrentSpeed(),
                leg.getVehicleHeading(),
                leg.getGpsProvider());
    }

    private LegDocumentResponse mapDoc(ShipmentLegDocument doc) {
        return new LegDocumentResponse(
                doc.getId(),
                doc.getLegId(),
                doc.getDocumentType(),
                doc.getFileName(),
                doc.getStorageUrl(),
                doc.getContentType(),
                doc.getRemarks(),
                doc.getCreatedAt());
    }

    private static boolean isTerminal(ShipmentStatus status) {
        return status == ShipmentStatus.CLOSED
                || status == ShipmentStatus.CANCELLED
                || status == ShipmentStatus.DELIVERED;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static BigDecimal z(BigDecimal n) {
        return n == null ? BigDecimal.ZERO : n;
    }

    private static String remarks(TransitionRequest r) {
        return r == null ? null : r.remarks();
    }

    private UUID org() {
        return TenantContext.getOrganizationId();
    }

    private void conflict(String message) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
