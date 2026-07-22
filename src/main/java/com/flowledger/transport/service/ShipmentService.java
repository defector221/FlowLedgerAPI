package com.flowledger.transport.service;

import static com.flowledger.transport.dto.TransportDtos.*;

import com.flowledger.common.tenant.TenantContext;
import com.flowledger.common.util.DocumentNumberService;
import com.flowledger.inventory.dto.InventoryDtos.PostTransaction;
import com.flowledger.inventory.entity.InventoryTransaction.Type;
import com.flowledger.inventory.service.InventoryService;
import com.flowledger.organization.repository.OrganizationRepository;
import com.flowledger.organization.repository.OrganizationSettingsRepository;
import com.flowledger.product.entity.Product;
import com.flowledger.sales.entity.DeliveryChallan;
import com.flowledger.sales.entity.DeliveryChallanItem;
import com.flowledger.sales.repository.DeliveryChallanRepository;
import com.flowledger.search.event.SearchIndexEventPublisher;
import com.flowledger.search.model.SearchEntityType;
import com.flowledger.transport.domain.TransportEnums.*;
import com.flowledger.transport.entity.*;
import com.flowledger.transport.repository.*;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class ShipmentService {
    private static final String NUMBER_FORMAT = "{PREFIX}/{FY}/{SEQ:6}";
    private final ShipmentRepository shipments;
    private final ShipmentLegRepository legs;
    private final ShipmentLineRepository lines;
    private final ShipmentEventRepository events;
    private final DeliveryChallanRepository challans;
    private final OrganizationRepository organizations;
    private final OrganizationSettingsRepository settings;
    private final DocumentNumberService numbers;
    private final ApprovalService approvals;
    private final IntegrationOutboxService outbox;
    private final InventoryService inventory;
    private final SearchIndexEventPublisher searchEvents;

    public ShipmentService(
            ShipmentRepository shipments,
            ShipmentLegRepository legs,
            ShipmentLineRepository lines,
            ShipmentEventRepository events,
            DeliveryChallanRepository challans,
            OrganizationRepository organizations,
            OrganizationSettingsRepository settings,
            DocumentNumberService numbers,
            ApprovalService approvals,
            IntegrationOutboxService outbox,
            InventoryService inventory,
            SearchIndexEventPublisher searchEvents) {
        this.shipments = shipments;
        this.legs = legs;
        this.lines = lines;
        this.events = events;
        this.challans = challans;
        this.organizations = organizations;
        this.settings = settings;
        this.numbers = numbers;
        this.approvals = approvals;
        this.outbox = outbox;
        this.inventory = inventory;
        this.searchEvents = searchEvents;
    }

    @Transactional(readOnly = true)
    public Page<ShipmentResponse> list(Pageable pageable) {
        return shipments.findAll(baseSpec(), pageable).map(this::map);
    }

    @Transactional(readOnly = true)
    public ShipmentResponse get(UUID id) {
        return map(load(id));
    }

    @Transactional(readOnly = true)
    public List<TimelineEvent> timeline(UUID id) {
        load(id);
        return events.findByShipmentIdOrderByOccurredAtAsc(id).stream()
                .map(event -> new TimelineEvent(
                        event.getId(),
                        event.getEventType(),
                        event.getOccurredAt(),
                        event.getActorUserId(),
                        event.getActorType(),
                        event.getRemarks(),
                        event.getLocationJson(),
                        event.getPayloadJson()))
                .toList();
    }

    public ShipmentResponse create(ShipmentRequest request) {
        Shipment shipment = new Shipment();
        shipment.setOrganizationId(org());
        shipment.setShipmentNumber(nextNumber());
        shipment.setStatus(ShipmentStatus.DRAFT);
        apply(shipment, request);
        audit(shipment, true);
        shipment = shipments.save(shipment);
        replaceLines(shipment.getId(), request.lines());
        replaceLegs(shipment.getId(), request.legs());
        event(shipment, "CREATED", request.remarks(), null, null);
        indexShipment(shipment);
        return map(shipment);
    }

    public ShipmentResponse update(UUID id, ShipmentRequest request) {
        Shipment shipment = load(id);
        requireStatus(shipment, ShipmentStatus.DRAFT);
        apply(shipment, request);
        audit(shipment, false);
        replaceLines(id, request.lines());
        replaceLegs(id, request.legs());
        event(shipment, "UPDATED", request.remarks(), null, null);
        shipment = shipments.save(shipment);
        indexShipment(shipment);
        return map(shipment);
    }

    public ShipmentResponse createFromChallan(UUID challanId, List<LineRequest> requestedLines) {
        DeliveryChallan challan = challans.findByIdAndOrganizationId(challanId, org())
                .orElseThrow(() -> notFound("Delivery challan not found"));
        List<LineRequest> selected = requestedLines == null || requestedLines.isEmpty()
                ? challan.getItems().stream()
                        .filter(i -> i.getQuantityRemaining().signum() > 0)
                        .map(i -> new LineRequest(
                                i.getId(),
                                i.getProductId(),
                                i.getDescription(),
                                i.getQuantityRemaining(),
                                i.getUnitId(),
                                null,
                                null,
                                i.getLineOrder()))
                        .toList()
                : requestedLines;
        validateChallanLines(challan, selected, false);
        ShipmentRequest request = new ShipmentRequest(
                "DELIVERY_CHALLAN",
                challan.getId(),
                challan.isTransportRequired(),
                null,
                null,
                null,
                challan.getWarehouseId(),
                "CUSTOMER",
                challan.getCustomerId(),
                null,
                null,
                null,
                BigDecimal.ZERO,
                null,
                null,
                null,
                null,
                null,
                challan.getNotes(),
                List.of(),
                selected);
        return create(request);
    }

    public ShipmentResponse submit(UUID id, DecisionRequest r) {
        Shipment shipment = load(id);
        requireStatus(shipment, ShipmentStatus.DRAFT);
        transition(shipment, ShipmentStatus.SUBMITTED, r == null ? null : r.remarks(), null, null);
        if (approvals.approvalRequired()) approvals.submit(shipment, r == null ? null : r.remarks());
        else transition(shipment, ShipmentStatus.APPROVED, "Approval not required", null, null);
        return map(shipment);
    }

    public ShipmentResponse approve(UUID id, DecisionRequest r) {
        Shipment shipment = load(id);
        requireStatus(shipment, ShipmentStatus.SUBMITTED);
        if (approvals.approvalRequired()) approvals.approve(shipment, r == null ? null : r.remarks());
        transition(shipment, ShipmentStatus.APPROVED, r == null ? null : r.remarks(), null, null);
        return map(shipment);
    }

    public ShipmentResponse reject(UUID id, DecisionRequest r) {
        Shipment shipment = load(id);
        requireStatus(shipment, ShipmentStatus.SUBMITTED);
        if (approvals.approvalRequired()) approvals.reject(shipment, r == null ? null : r.remarks());
        transition(shipment, ShipmentStatus.REJECTED, r == null ? null : r.remarks(), null, null);
        return map(shipment);
    }

    public ShipmentResponse assign(UUID id, AssignmentRequest r) {
        Shipment shipment = load(id);
        requireStatus(shipment, ShipmentStatus.APPROVED);
        replaceLegs(id, r.legs());
        transition(shipment, ShipmentStatus.ASSIGNED, r.remarks(), null, null);
        return map(shipment);
    }

    public ShipmentResponse startLoading(UUID id, TransitionRequest r) {
        return simple(id, ShipmentStatus.ASSIGNED, ShipmentStatus.LOADING, r);
    }

    public ShipmentResponse loaded(UUID id, TransitionRequest r) {
        return simple(id, ShipmentStatus.LOADING, ShipmentStatus.LOADED, r);
    }

    public ShipmentResponse dispatch(UUID id, TransitionRequest r) {
        Shipment shipment = load(id);
        requireStatus(shipment, ShipmentStatus.LOADED);
        validateDispatch(shipment);
        ShipmentStatus target = postChallanDispatch(shipment);
        shipment.setActualDispatchDate(OffsetDateTime.now());
        transition(shipment, target, remarks(r), location(r), payload(r));
        maybePostInventoryOnDispatch(shipment);
        outbox.enqueue(
                "SHIPMENT_DISPATCHED",
                "SHIPMENT",
                shipment.getId(),
                "{\"shipmentNumber\":\"" + shipment.getShipmentNumber() + "\",\"status\":\"" + target + "\"}");
        return map(shipment);
    }

    public ShipmentResponse checkpoint(UUID id, TransitionRequest r) {
        Shipment shipment = load(id);
        if (shipment.getStatus() != ShipmentStatus.DISPATCHED
                && shipment.getStatus() != ShipmentStatus.PARTIALLY_DISPATCHED
                && shipment.getStatus() != ShipmentStatus.IN_TRANSIT) conflict("Shipment is not dispatched");
        if (shipment.getStatus() == ShipmentStatus.IN_TRANSIT)
            event(shipment, "CHECKPOINT", remarks(r), location(r), payload(r));
        else transition(shipment, ShipmentStatus.IN_TRANSIT, remarks(r), location(r), payload(r));
        return map(shipment);
    }

    public ShipmentResponse deliver(UUID id, TransitionRequest r) {
        Shipment shipment = load(id);
        requireStatus(shipment, ShipmentStatus.IN_TRANSIT);
        shipment.setActualDeliveryDate(OffsetDateTime.now());
        transition(shipment, ShipmentStatus.DELIVERED, remarks(r), location(r), payload(r));
        outbox.enqueue(
                "SHIPMENT_DELIVERED",
                "SHIPMENT",
                shipment.getId(),
                "{\"shipmentNumber\":\"" + shipment.getShipmentNumber() + "\"}");
        return map(shipment);
    }

    public ShipmentResponse close(UUID id, TransitionRequest r) {
        return simple(id, ShipmentStatus.DELIVERED, ShipmentStatus.CLOSED, r);
    }

    public ShipmentResponse cancel(UUID id, TransitionRequest r) {
        Shipment shipment = load(id);
        if (EnumSet.of(ShipmentStatus.CLOSED, ShipmentStatus.CANCELLED, ShipmentStatus.DELIVERED)
                .contains(shipment.getStatus())) conflict("Shipment cannot be cancelled in " + shipment.getStatus());
        transition(shipment, ShipmentStatus.CANCELLED, remarks(r), location(r), payload(r));
        return map(shipment);
    }

    @Transactional(readOnly = true)
    public Page<ShipmentResponse> search(SearchRequest r, Pageable pageable) {
        Specification<Shipment> spec = baseSpec().and((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (text(r.q())) {
                String searchQuery = like(r.q());
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("shipmentNumber")), searchQuery),
                        cb.like(cb.lower(root.get("shipToAddress")), searchQuery),
                        cb.like(cb.lower(root.get("remarks")), searchQuery)));
            }
            if (r.status() != null) predicates.add(cb.equal(root.get("status"), r.status()));
            if (r.customerId() != null) predicates.add(cb.equal(root.get("shipToPartyId"), r.customerId()));
            if (r.warehouseId() != null) predicates.add(cb.equal(root.get("fromWarehouseId"), r.warehouseId()));
            if (text(r.sourceDocumentType()))
                predicates.add(cb.equal(
                        cb.upper(root.get("sourceDocumentType")),
                        r.sourceDocumentType().toUpperCase()));
            if (r.sourceDocumentId() != null)
                predicates.add(cb.equal(root.get("sourceDocumentId"), r.sourceDocumentId()));
            if (text(r.ewayBillNumber()))
                predicates.add(cb.like(cb.lower(root.get("ewayBillNumber")), like(r.ewayBillNumber())));
            if (r.fromDate() != null)
                predicates.add(cb.greaterThanOrEqualTo(
                        root.get("createdAt"), r.fromDate().atStartOfDay().atOffset(ZoneOffset.UTC)));
            if (r.toDate() != null)
                predicates.add(cb.lessThan(
                        root.get("createdAt"),
                        r.toDate().plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC)));
            if (text(r.lrNumber())) {
                Subquery<UUID> sq = query.subquery(UUID.class);
                var leg = sq.from(ShipmentLeg.class);
                sq.select(leg.get("shipmentId"))
                        .where(
                                cb.equal(leg.get("shipmentId"), root.get("id")),
                                cb.like(cb.lower(leg.get("lrNumber")), like(r.lrNumber())));
                predicates.add(cb.exists(sq));
            }
            if (text(r.vehicleNumber())) {
                Subquery<UUID> snapshot = query.subquery(UUID.class);
                var leg = snapshot.from(ShipmentLeg.class);
                snapshot.select(leg.get("shipmentId"))
                        .where(
                                cb.equal(leg.get("shipmentId"), root.get("id")),
                                cb.like(cb.lower(leg.get("vehicleNumberSnapshot")), like(r.vehicleNumber())));
                Subquery<UUID> master = query.subquery(UUID.class);
                var masterLeg = master.from(ShipmentLeg.class);
                var vehicle = master.from(TransportVehicle.class);
                master.select(masterLeg.get("shipmentId"))
                        .where(
                                cb.equal(masterLeg.get("shipmentId"), root.get("id")),
                                cb.equal(masterLeg.get("vehicleId"), vehicle.get("id")),
                                cb.like(cb.lower(vehicle.get("vehicleNumber")), like(r.vehicleNumber())));
                predicates.add(cb.or(cb.exists(snapshot), cb.exists(master)));
            }
            if (text(r.driverName()) || text(r.driverMobile())) {
                Subquery<UUID> snapshot = query.subquery(UUID.class);
                var leg = snapshot.from(ShipmentLeg.class);
                List<Predicate> snapshotPredicates = new ArrayList<>();
                snapshotPredicates.add(cb.equal(leg.get("shipmentId"), root.get("id")));
                if (text(r.driverName()))
                    snapshotPredicates.add(cb.like(cb.lower(leg.get("driverNameSnapshot")), like(r.driverName())));
                if (text(r.driverMobile()))
                    snapshotPredicates.add(cb.like(cb.lower(leg.get("driverMobileSnapshot")), like(r.driverMobile())));
                snapshot.select(leg.get("shipmentId")).where(snapshotPredicates.toArray(Predicate[]::new));
                Subquery<UUID> master = query.subquery(UUID.class);
                var masterLeg = master.from(ShipmentLeg.class);
                var driver = master.from(TransportDriver.class);
                List<Predicate> masterPredicates = new ArrayList<>();
                masterPredicates.add(cb.equal(masterLeg.get("shipmentId"), root.get("id")));
                masterPredicates.add(cb.equal(masterLeg.get("driverId"), driver.get("id")));
                if (text(r.driverName()))
                    masterPredicates.add(cb.like(cb.lower(driver.get("name")), like(r.driverName())));
                if (text(r.driverMobile()))
                    masterPredicates.add(cb.like(cb.lower(driver.get("mobile")), like(r.driverMobile())));
                master.select(masterLeg.get("shipmentId")).where(masterPredicates.toArray(Predicate[]::new));
                predicates.add(cb.or(cb.exists(snapshot), cb.exists(master)));
            }
            if (text(r.company())) {
                Subquery<UUID> direct = query.subquery(UUID.class);
                var company = direct.from(TransportCompany.class);
                direct.select(company.get("id"))
                        .where(
                                cb.equal(company.get("id"), root.get("transportCompanyId")),
                                cb.like(cb.lower(company.get("name")), like(r.company())));
                Subquery<UUID> legCompany = query.subquery(UUID.class);
                var leg = legCompany.from(ShipmentLeg.class);
                var nestedCompany = legCompany.from(TransportCompany.class);
                legCompany
                        .select(leg.get("shipmentId"))
                        .where(
                                cb.equal(leg.get("shipmentId"), root.get("id")),
                                cb.equal(leg.get("transportCompanyId"), nestedCompany.get("id")),
                                cb.like(cb.lower(nestedCompany.get("name")), like(r.company())));
                predicates.add(cb.or(cb.exists(direct), cb.exists(legCompany)));
            }
            if (text(r.sku())) {
                Subquery<UUID> sq = query.subquery(UUID.class);
                var line = sq.from(ShipmentLine.class);
                var product = sq.from(Product.class);
                sq.select(line.get("shipmentId"))
                        .where(
                                cb.equal(line.get("shipmentId"), root.get("id")),
                                cb.equal(line.get("productId"), product.get("id")),
                                cb.like(cb.lower(product.get("sku")), like(r.sku())));
                predicates.add(cb.exists(sq));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        });
        return shipments.findAll(spec, pageable).map(this::map);
    }

    private ShipmentResponse simple(UUID id, ShipmentStatus from, ShipmentStatus to, TransitionRequest r) {
        Shipment shipment = load(id);
        requireStatus(shipment, from);
        transition(shipment, to, remarks(r), location(r), payload(r));
        return map(shipment);
    }

    private void validateDispatch(Shipment shipment) {
        if (!shipment.isTransportRequired()) return;
        if (shipment.getTransportMode() == null || shipment.getTransportType() == null)
            conflict("Transport mode and type are required");
        List<ShipmentLeg> assigned = legs.findByShipmentIdOrderBySequenceNo(shipment.getId());
        if (assigned.isEmpty()) conflict("At least one shipment leg is required");
        boolean invalid = assigned.stream()
                .anyMatch(leg -> leg.getTransportCompanyId() == null
                        || (leg.getVehicleId() == null && !text(leg.getVehicleNumberSnapshot()))
                        || leg.getDriverId() == null);
        if (invalid) conflict("Each leg requires transporter, driver, and vehicle or vehicle number");
    }

    private ShipmentStatus postChallanDispatch(Shipment shipment) {
        if (!"DELIVERY_CHALLAN".equalsIgnoreCase(shipment.getSourceDocumentType())
                || shipment.getSourceDocumentId() == null) return ShipmentStatus.DISPATCHED;
        DeliveryChallan challan = challans.findByIdAndOrganizationId(shipment.getSourceDocumentId(), org())
                .orElseThrow(() -> notFound("Delivery challan not found"));
        List<ShipmentLine> shipmentLines = lines.findByShipmentIdOrderByLineOrder(shipment.getId());
        validateChallanLines(challan, shipmentLines.stream().map(this::request).toList(), true);
        Map<UUID, DeliveryChallanItem> byId = new HashMap<>();
        challan.getItems().forEach(i -> byId.put(i.getId(), i));
        shipmentLines.forEach(line -> {
            DeliveryChallanItem item = byId.get(line.getSourceLineId());
            item.setQuantityDispatched(z(item.getQuantityDispatched()).add(line.getQuantity()));
        });
        challans.saveAndFlush(challan);
        boolean remaining = challan.getItems().stream()
                .anyMatch(i -> i.getQuantityRemaining().signum() > 0);
        return remaining ? ShipmentStatus.PARTIALLY_DISPATCHED : ShipmentStatus.DISPATCHED;
    }

    private void validateChallanLines(DeliveryChallan challan, List<LineRequest> requested, boolean dispatch) {
        Map<UUID, DeliveryChallanItem> byId = new HashMap<>();
        challan.getItems().forEach(i -> byId.put(i.getId(), i));
        for (LineRequest line : requested) {
            DeliveryChallanItem item = byId.get(line.sourceLineId());
            if (item == null || !item.getProductId().equals(line.productId()))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid challan source line");
            if (line.quantity().compareTo(item.getQuantityRemaining()) > 0)
                conflict((dispatch ? "Dispatch" : "Shipment") + " quantity exceeds challan remaining quantity");
        }
    }

    public void maybePostInventoryOnDispatch(Shipment shipment) {
        String event = settings.findByOrganizationId(org())
                .map(x -> x.getInventoryDeductionEvent())
                .orElse("");
        if (!Set.of("DELIVERY_CHALLAN", "CHALLAN_DISPATCH").contains(event.toUpperCase(Locale.ROOT))) return;
        if (shipment.getFromWarehouseId() == null) conflict("Warehouse is required for inventory deduction");
        for (ShipmentLine line : lines.findByShipmentIdOrderByLineOrder(shipment.getId())) {
            inventory.postTransaction(new PostTransaction(
                    Type.SALE,
                    line.getProductId(),
                    shipment.getFromWarehouseId(),
                    BigDecimal.ZERO,
                    line.getQuantity(),
                    "SHIPMENT",
                    shipment.getId(),
                    shipment.getShipmentNumber(),
                    "shipment:" + shipment.getId() + ":" + line.getId(),
                    line.getBatchNumber(),
                    line.getSerialNumber(),
                    null,
                    BigDecimal.ZERO,
                    shipment.getRemarks(),
                    LocalDate.now()));
        }
    }

    private void apply(Shipment shipment, ShipmentRequest r) {
        shipment.setSourceDocumentType(r.sourceDocumentType());
        shipment.setSourceDocumentId(r.sourceDocumentId());
        shipment.setTransportRequired(r.transportRequired());
        shipment.setTransportMode(r.transportMode());
        shipment.setTransportType(r.transportType());
        shipment.setTransportCompanyId(r.transportCompanyId());
        shipment.setFromWarehouseId(r.fromWarehouseId());
        shipment.setShipToPartyType(r.shipToPartyType());
        shipment.setShipToPartyId(r.shipToPartyId());
        shipment.setShipToAddress(r.shipToAddress());
        shipment.setExpectedDispatchDate(r.expectedDispatchDate());
        shipment.setExpectedDeliveryDate(r.expectedDeliveryDate());
        shipment.setFreightCharges(z(r.freightCharges()));
        shipment.setFreightPaidBy(r.freightPaidBy());
        shipment.setInsuranceDetails(r.insuranceDetails());
        shipment.setGpsTrackingUrl(r.gpsTrackingUrl());
        shipment.setEwayBillNumber(r.ewayBillNumber());
        shipment.setEinvoiceReference(r.einvoiceReference());
        shipment.setRemarks(r.remarks());
    }

    private void replaceLines(UUID shipmentId, List<LineRequest> requested) {
        lines.deleteByShipmentId(shipmentId);
        int n = 0;
        for (LineRequest r : requested) {
            ShipmentLine line = new ShipmentLine();
            line.setShipmentId(shipmentId);
            line.setSourceLineId(r.sourceLineId());
            line.setProductId(r.productId());
            line.setDescription(r.description());
            line.setQuantity(r.quantity());
            line.setUnitId(r.unitId());
            line.setBatchNumber(r.batchNumber());
            line.setSerialNumber(r.serialNumber());
            line.setLineOrder(r.lineOrder() == null ? n++ : r.lineOrder());
            lines.save(line);
        }
    }

    private void replaceLegs(UUID shipmentId, List<LegRequest> requested) {
        legs.deleteByShipmentId(shipmentId);
        int n = 1;
        for (LegRequest r : requested == null ? List.<LegRequest>of() : requested) {
            ShipmentLeg leg = new ShipmentLeg();
            leg.setShipmentId(shipmentId);
            leg.setSequenceNo(r.sequenceNo() == null ? n++ : r.sequenceNo());
            leg.setTransportCompanyId(r.transportCompanyId());
            leg.setVehicleId(r.vehicleId());
            leg.setDriverId(r.driverId());
            leg.setLrNumber(r.lrNumber());
            leg.setConsignmentNumber(r.consignmentNumber());
            leg.setVehicleNumberSnapshot(r.vehicleNumberSnapshot());
            leg.setDriverNameSnapshot(r.driverNameSnapshot());
            leg.setDriverMobileSnapshot(r.driverMobileSnapshot());
            leg.setExpectedDeparture(r.expectedDeparture());
            leg.setExpectedArrival(r.expectedArrival());
            leg.setRemarks(r.remarks());
            legs.save(leg);
        }
    }

    private void transition(Shipment shipment, ShipmentStatus status, String remarks, String location, String payload) {
        shipment.setStatus(status);
        audit(shipment, false);
        shipments.save(shipment);
        event(shipment, status.name(), remarks, location, payload);
        indexShipment(shipment);
    }

    private void indexShipment(Shipment shipment) {
        searchEvents.upsert(shipment.getOrganizationId(), SearchEntityType.SHIPMENT, shipment.getId());
    }

    private void event(Shipment shipment, String type, String remarks, String location, String payload) {
        ShipmentEvent event = new ShipmentEvent();
        event.setShipmentId(shipment.getId());
        event.setEventType(type);
        event.setOccurredAt(OffsetDateTime.now());
        event.setActorUserId(TenantContext.userId().orElse(null));
        event.setActorType(TenantContext.userId().isPresent() ? ShipmentActorType.USER : ShipmentActorType.SYSTEM);
        event.setRemarks(remarks);
        event.setLocationJson(location);
        event.setPayloadJson(payload);
        events.save(event);
    }

    private Shipment load(UUID id) {
        return shipments
                .findByIdAndOrganizationIdAndDeletedFalse(id, org())
                .orElseThrow(() -> notFound("Shipment not found"));
    }

    private void requireStatus(Shipment shipment, ShipmentStatus expected) {
        if (shipment.getStatus() != expected)
            conflict("Expected " + expected + " but shipment is " + shipment.getStatus());
    }

    private Specification<Shipment> baseSpec() {
        UUID org = org();
        return (root, query, cb) -> cb.and(cb.equal(root.get("organizationId"), org), cb.isFalse(root.get("deleted")));
    }

    private String nextNumber() {
        var organization = organizations.findById(org()).orElseThrow(() -> notFound("Organization not found"));
        return numbers.next(
                org(), "SHIPMENT", "SHP", NUMBER_FORMAT, organization.getFinancialYearStart(), LocalDate.now());
    }

    private ShipmentResponse map(Shipment shipment) {
        List<LegResponse> lr = legs.findByShipmentIdOrderBySequenceNo(shipment.getId()).stream()
                .map(leg -> new LegResponse(
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
                        leg.getRemarks()))
                .toList();
        List<LineResponse> li = lines.findByShipmentIdOrderByLineOrder(shipment.getId()).stream()
                .map(line -> new LineResponse(
                        line.getId(),
                        line.getSourceLineId(),
                        line.getProductId(),
                        line.getDescription(),
                        line.getQuantity(),
                        line.getUnitId(),
                        line.getBatchNumber(),
                        line.getSerialNumber(),
                        line.getLineOrder()))
                .toList();
        return new ShipmentResponse(
                shipment.getId(),
                shipment.getShipmentNumber(),
                shipment.getStatus(),
                shipment.getSourceDocumentType(),
                shipment.getSourceDocumentId(),
                shipment.isTransportRequired(),
                shipment.getTransportMode(),
                shipment.getTransportType(),
                shipment.getTransportCompanyId(),
                shipment.getFromWarehouseId(),
                shipment.getShipToPartyType(),
                shipment.getShipToPartyId(),
                shipment.getShipToAddress(),
                shipment.getExpectedDispatchDate(),
                shipment.getExpectedDeliveryDate(),
                shipment.getActualDispatchDate(),
                shipment.getActualDeliveryDate(),
                shipment.getFreightCharges(),
                shipment.getFreightPaidBy(),
                shipment.getInsuranceDetails(),
                shipment.getGpsTrackingUrl(),
                shipment.getEwayBillNumber(),
                shipment.getEinvoiceReference(),
                shipment.getRemarks(),
                lr,
                li,
                shipment.getVersion());
    }

    private LineRequest request(ShipmentLine line) {
        return new LineRequest(
                line.getSourceLineId(),
                line.getProductId(),
                line.getDescription(),
                line.getQuantity(),
                line.getUnitId(),
                line.getBatchNumber(),
                line.getSerialNumber(),
                line.getLineOrder());
    }

    private UUID org() {
        return TenantContext.getOrganizationId();
    }

    private void audit(Shipment shipment, boolean created) {
        TenantContext.userId().ifPresent(u -> {
            if (created) shipment.setCreatedBy(u);
            shipment.setUpdatedBy(u);
        });
    }

    private static boolean text(String s) {
        return s != null && !s.isBlank();
    }

    private static String like(String s) {
        return "%" + s.toLowerCase(Locale.ROOT) + "%";
    }

    private static BigDecimal z(BigDecimal n) {
        return n == null ? BigDecimal.ZERO : n;
    }

    private static String remarks(TransitionRequest r) {
        return r == null ? null : r.remarks();
    }

    private static String location(TransitionRequest r) {
        return r == null ? null : r.locationJson();
    }

    private static String payload(TransitionRequest r) {
        return r == null ? null : r.payloadJson();
    }

    private ResponseStatusException notFound(String m) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, m);
    }

    private void conflict(String m) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, m);
    }
}
