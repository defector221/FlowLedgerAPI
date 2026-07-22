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

    public ShipmentService(ShipmentRepository shipments, ShipmentLegRepository legs,
            ShipmentLineRepository lines, ShipmentEventRepository events,
            DeliveryChallanRepository challans, OrganizationRepository organizations,
            OrganizationSettingsRepository settings, DocumentNumberService numbers,
            ApprovalService approvals, IntegrationOutboxService outbox, InventoryService inventory,
            SearchIndexEventPublisher searchEvents) {
        this.shipments = shipments; this.legs = legs; this.lines = lines; this.events = events;
        this.challans = challans; this.organizations = organizations; this.settings = settings;
        this.numbers = numbers; this.approvals = approvals; this.outbox = outbox; this.inventory = inventory;
        this.searchEvents = searchEvents;
    }

    @Transactional(readOnly = true)
    public Page<ShipmentResponse> list(Pageable pageable) {
        return shipments.findAll(baseSpec(), pageable).map(this::map);
    }

    @Transactional(readOnly = true)
    public ShipmentResponse get(UUID id) { return map(load(id)); }

    @Transactional(readOnly = true)
    public List<TimelineEvent> timeline(UUID id) {
        load(id);
        return events.findByShipmentIdOrderByOccurredAtAsc(id).stream()
                .map(e -> new TimelineEvent(e.getId(), e.getEventType(), e.getOccurredAt(),
                        e.getActorUserId(), e.getActorType(), e.getRemarks(), e.getLocationJson(), e.getPayloadJson()))
                .toList();
    }

    public ShipmentResponse create(ShipmentRequest request) {
        Shipment s = new Shipment();
        s.setOrganizationId(org());
        s.setShipmentNumber(nextNumber());
        s.setStatus(ShipmentStatus.DRAFT);
        apply(s, request);
        audit(s, true);
        s = shipments.save(s);
        replaceLines(s.getId(), request.lines());
        replaceLegs(s.getId(), request.legs());
        event(s, "CREATED", request.remarks(), null, null);
        indexShipment(s);
        return map(s);
    }

    public ShipmentResponse update(UUID id, ShipmentRequest request) {
        Shipment s = load(id);
        requireStatus(s, ShipmentStatus.DRAFT);
        apply(s, request);
        audit(s, false);
        replaceLines(id, request.lines());
        replaceLegs(id, request.legs());
        event(s, "UPDATED", request.remarks(), null, null);
        s = shipments.save(s);
        indexShipment(s);
        return map(s);
    }

    public ShipmentResponse createFromChallan(UUID challanId, List<LineRequest> requestedLines) {
        DeliveryChallan c = challans.findByIdAndOrganizationId(challanId, org())
                .orElseThrow(() -> notFound("Delivery challan not found"));
        List<LineRequest> selected = requestedLines == null || requestedLines.isEmpty()
                ? c.getItems().stream().filter(i -> i.getQuantityRemaining().signum() > 0)
                        .map(i -> new LineRequest(i.getId(), i.getProductId(), i.getDescription(),
                                i.getQuantityRemaining(), i.getUnitId(), null, null, i.getLineOrder())).toList()
                : requestedLines;
        validateChallanLines(c, selected, false);
        ShipmentRequest request = new ShipmentRequest("DELIVERY_CHALLAN", c.getId(), c.isTransportRequired(),
                null, null, null, c.getWarehouseId(), "CUSTOMER", c.getCustomerId(), null,
                null, null, BigDecimal.ZERO, null, null, null, null, null, c.getNotes(), List.of(), selected);
        return create(request);
    }

    public ShipmentResponse submit(UUID id, DecisionRequest r) {
        Shipment s = load(id); requireStatus(s, ShipmentStatus.DRAFT);
        transition(s, ShipmentStatus.SUBMITTED, r == null ? null : r.remarks(), null, null);
        if (approvals.approvalRequired()) approvals.submit(s, r == null ? null : r.remarks());
        else transition(s, ShipmentStatus.APPROVED, "Approval not required", null, null);
        return map(s);
    }

    public ShipmentResponse approve(UUID id, DecisionRequest r) {
        Shipment s = load(id); requireStatus(s, ShipmentStatus.SUBMITTED);
        if (approvals.approvalRequired()) approvals.approve(s, r == null ? null : r.remarks());
        transition(s, ShipmentStatus.APPROVED, r == null ? null : r.remarks(), null, null);
        return map(s);
    }

    public ShipmentResponse reject(UUID id, DecisionRequest r) {
        Shipment s = load(id); requireStatus(s, ShipmentStatus.SUBMITTED);
        if (approvals.approvalRequired()) approvals.reject(s, r == null ? null : r.remarks());
        transition(s, ShipmentStatus.REJECTED, r == null ? null : r.remarks(), null, null);
        return map(s);
    }

    public ShipmentResponse assign(UUID id, AssignmentRequest r) {
        Shipment s = load(id); requireStatus(s, ShipmentStatus.APPROVED);
        replaceLegs(id, r.legs());
        transition(s, ShipmentStatus.ASSIGNED, r.remarks(), null, null);
        return map(s);
    }

    public ShipmentResponse startLoading(UUID id, TransitionRequest r) { return simple(id, ShipmentStatus.ASSIGNED, ShipmentStatus.LOADING, r); }
    public ShipmentResponse loaded(UUID id, TransitionRequest r) { return simple(id, ShipmentStatus.LOADING, ShipmentStatus.LOADED, r); }

    public ShipmentResponse dispatch(UUID id, TransitionRequest r) {
        Shipment s = load(id); requireStatus(s, ShipmentStatus.LOADED);
        validateDispatch(s);
        ShipmentStatus target = postChallanDispatch(s);
        s.setActualDispatchDate(OffsetDateTime.now());
        transition(s, target, remarks(r), location(r), payload(r));
        maybePostInventoryOnDispatch(s);
        outbox.enqueue("SHIPMENT_DISPATCHED", "SHIPMENT", s.getId(),
                "{\"shipmentNumber\":\"" + s.getShipmentNumber() + "\",\"status\":\"" + target + "\"}");
        return map(s);
    }

    public ShipmentResponse checkpoint(UUID id, TransitionRequest r) {
        Shipment s = load(id);
        if (s.getStatus() != ShipmentStatus.DISPATCHED && s.getStatus() != ShipmentStatus.PARTIALLY_DISPATCHED
                && s.getStatus() != ShipmentStatus.IN_TRANSIT) conflict("Shipment is not dispatched");
        if (s.getStatus() == ShipmentStatus.IN_TRANSIT) event(s, "CHECKPOINT", remarks(r), location(r), payload(r));
        else transition(s, ShipmentStatus.IN_TRANSIT, remarks(r), location(r), payload(r));
        return map(s);
    }

    public ShipmentResponse deliver(UUID id, TransitionRequest r) {
        Shipment s = load(id); requireStatus(s, ShipmentStatus.IN_TRANSIT);
        s.setActualDeliveryDate(OffsetDateTime.now());
        transition(s, ShipmentStatus.DELIVERED, remarks(r), location(r), payload(r));
        outbox.enqueue("SHIPMENT_DELIVERED", "SHIPMENT", s.getId(),
                "{\"shipmentNumber\":\"" + s.getShipmentNumber() + "\"}");
        return map(s);
    }

    public ShipmentResponse close(UUID id, TransitionRequest r) { return simple(id, ShipmentStatus.DELIVERED, ShipmentStatus.CLOSED, r); }

    public ShipmentResponse cancel(UUID id, TransitionRequest r) {
        Shipment s = load(id);
        if (EnumSet.of(ShipmentStatus.CLOSED, ShipmentStatus.CANCELLED, ShipmentStatus.DELIVERED).contains(s.getStatus()))
            conflict("Shipment cannot be cancelled in " + s.getStatus());
        transition(s, ShipmentStatus.CANCELLED, remarks(r), location(r), payload(r));
        return map(s);
    }

    @Transactional(readOnly = true)
    public Page<ShipmentResponse> search(SearchRequest r, Pageable pageable) {
        Specification<Shipment> spec = baseSpec().and((root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (text(r.q())) {
                String q = like(r.q());
                p.add(cb.or(cb.like(cb.lower(root.get("shipmentNumber")), q),
                        cb.like(cb.lower(root.get("shipToAddress")), q),
                        cb.like(cb.lower(root.get("remarks")), q)));
            }
            if (r.status() != null) p.add(cb.equal(root.get("status"), r.status()));
            if (r.customerId() != null) p.add(cb.equal(root.get("shipToPartyId"), r.customerId()));
            if (r.warehouseId() != null) p.add(cb.equal(root.get("fromWarehouseId"), r.warehouseId()));
            if (text(r.sourceDocumentType())) p.add(cb.equal(cb.upper(root.get("sourceDocumentType")), r.sourceDocumentType().toUpperCase()));
            if (r.sourceDocumentId() != null) p.add(cb.equal(root.get("sourceDocumentId"), r.sourceDocumentId()));
            if (text(r.ewayBillNumber())) p.add(cb.like(cb.lower(root.get("ewayBillNumber")), like(r.ewayBillNumber())));
            if (r.fromDate() != null) p.add(cb.greaterThanOrEqualTo(root.get("createdAt"), r.fromDate().atStartOfDay().atOffset(ZoneOffset.UTC)));
            if (r.toDate() != null) p.add(cb.lessThan(root.get("createdAt"), r.toDate().plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC)));
            if (text(r.lrNumber())) {
                Subquery<UUID> sq = query.subquery(UUID.class);
                var leg = sq.from(ShipmentLeg.class);
                sq.select(leg.get("shipmentId")).where(cb.equal(leg.get("shipmentId"), root.get("id")),
                        cb.like(cb.lower(leg.get("lrNumber")), like(r.lrNumber())));
                p.add(cb.exists(sq));
            }
            if (text(r.vehicleNumber())) {
                Subquery<UUID> snapshot = query.subquery(UUID.class);
                var leg = snapshot.from(ShipmentLeg.class);
                snapshot.select(leg.get("shipmentId")).where(cb.equal(leg.get("shipmentId"), root.get("id")),
                        cb.like(cb.lower(leg.get("vehicleNumberSnapshot")), like(r.vehicleNumber())));
                Subquery<UUID> master = query.subquery(UUID.class);
                var masterLeg = master.from(ShipmentLeg.class);
                var vehicle = master.from(TransportVehicle.class);
                master.select(masterLeg.get("shipmentId")).where(cb.equal(masterLeg.get("shipmentId"), root.get("id")),
                        cb.equal(masterLeg.get("vehicleId"), vehicle.get("id")),
                        cb.like(cb.lower(vehicle.get("vehicleNumber")), like(r.vehicleNumber())));
                p.add(cb.or(cb.exists(snapshot), cb.exists(master)));
            }
            if (text(r.driverName()) || text(r.driverMobile())) {
                Subquery<UUID> snapshot = query.subquery(UUID.class);
                var leg = snapshot.from(ShipmentLeg.class);
                List<Predicate> snapshotPredicates = new ArrayList<>();
                snapshotPredicates.add(cb.equal(leg.get("shipmentId"), root.get("id")));
                if (text(r.driverName())) snapshotPredicates.add(cb.like(cb.lower(leg.get("driverNameSnapshot")), like(r.driverName())));
                if (text(r.driverMobile())) snapshotPredicates.add(cb.like(cb.lower(leg.get("driverMobileSnapshot")), like(r.driverMobile())));
                snapshot.select(leg.get("shipmentId")).where(snapshotPredicates.toArray(Predicate[]::new));
                Subquery<UUID> master = query.subquery(UUID.class);
                var masterLeg = master.from(ShipmentLeg.class);
                var driver = master.from(TransportDriver.class);
                List<Predicate> masterPredicates = new ArrayList<>();
                masterPredicates.add(cb.equal(masterLeg.get("shipmentId"), root.get("id")));
                masterPredicates.add(cb.equal(masterLeg.get("driverId"), driver.get("id")));
                if (text(r.driverName())) masterPredicates.add(cb.like(cb.lower(driver.get("name")), like(r.driverName())));
                if (text(r.driverMobile())) masterPredicates.add(cb.like(cb.lower(driver.get("mobile")), like(r.driverMobile())));
                master.select(masterLeg.get("shipmentId")).where(masterPredicates.toArray(Predicate[]::new));
                p.add(cb.or(cb.exists(snapshot), cb.exists(master)));
            }
            if (text(r.company())) {
                Subquery<UUID> direct = query.subquery(UUID.class);
                var company = direct.from(TransportCompany.class);
                direct.select(company.get("id")).where(cb.equal(company.get("id"), root.get("transportCompanyId")),
                        cb.like(cb.lower(company.get("name")), like(r.company())));
                Subquery<UUID> legCompany = query.subquery(UUID.class);
                var leg = legCompany.from(ShipmentLeg.class);
                var nestedCompany = legCompany.from(TransportCompany.class);
                legCompany.select(leg.get("shipmentId")).where(cb.equal(leg.get("shipmentId"), root.get("id")),
                        cb.equal(leg.get("transportCompanyId"), nestedCompany.get("id")),
                        cb.like(cb.lower(nestedCompany.get("name")), like(r.company())));
                p.add(cb.or(cb.exists(direct), cb.exists(legCompany)));
            }
            if (text(r.sku())) {
                Subquery<UUID> sq = query.subquery(UUID.class);
                var line = sq.from(ShipmentLine.class);
                var product = sq.from(Product.class);
                sq.select(line.get("shipmentId")).where(cb.equal(line.get("shipmentId"), root.get("id")),
                        cb.equal(line.get("productId"), product.get("id")), cb.like(cb.lower(product.get("sku")), like(r.sku())));
                p.add(cb.exists(sq));
            }
            return cb.and(p.toArray(Predicate[]::new));
        });
        return shipments.findAll(spec, pageable).map(this::map);
    }

    private ShipmentResponse simple(UUID id, ShipmentStatus from, ShipmentStatus to, TransitionRequest r) {
        Shipment s = load(id); requireStatus(s, from); transition(s, to, remarks(r), location(r), payload(r)); return map(s);
    }

    private void validateDispatch(Shipment s) {
        if (!s.isTransportRequired()) return;
        if (s.getTransportMode() == null || s.getTransportType() == null) conflict("Transport mode and type are required");
        List<ShipmentLeg> assigned = legs.findByShipmentIdOrderBySequenceNo(s.getId());
        if (assigned.isEmpty()) conflict("At least one shipment leg is required");
        boolean invalid = assigned.stream().anyMatch(l -> l.getTransportCompanyId() == null
                || (l.getVehicleId() == null && !text(l.getVehicleNumberSnapshot())) || l.getDriverId() == null);
        if (invalid) conflict("Each leg requires transporter, driver, and vehicle or vehicle number");
    }

    private ShipmentStatus postChallanDispatch(Shipment s) {
        if (!"DELIVERY_CHALLAN".equalsIgnoreCase(s.getSourceDocumentType()) || s.getSourceDocumentId() == null)
            return ShipmentStatus.DISPATCHED;
        DeliveryChallan c = challans.findByIdAndOrganizationId(s.getSourceDocumentId(), org())
                .orElseThrow(() -> notFound("Delivery challan not found"));
        List<ShipmentLine> shipmentLines = lines.findByShipmentIdOrderByLineOrder(s.getId());
        validateChallanLines(c, shipmentLines.stream().map(this::request).toList(), true);
        Map<UUID, DeliveryChallanItem> byId = new HashMap<>();
        c.getItems().forEach(i -> byId.put(i.getId(), i));
        shipmentLines.forEach(line -> {
            DeliveryChallanItem item = byId.get(line.getSourceLineId());
            item.setQuantityDispatched(z(item.getQuantityDispatched()).add(line.getQuantity()));
        });
        challans.saveAndFlush(c);
        boolean remaining = c.getItems().stream().anyMatch(i -> i.getQuantityRemaining().signum() > 0);
        return remaining ? ShipmentStatus.PARTIALLY_DISPATCHED : ShipmentStatus.DISPATCHED;
    }

    private void validateChallanLines(DeliveryChallan c, List<LineRequest> requested, boolean dispatch) {
        Map<UUID, DeliveryChallanItem> byId = new HashMap<>();
        c.getItems().forEach(i -> byId.put(i.getId(), i));
        for (LineRequest line : requested) {
            DeliveryChallanItem item = byId.get(line.sourceLineId());
            if (item == null || !item.getProductId().equals(line.productId())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid challan source line");
            if (line.quantity().compareTo(item.getQuantityRemaining()) > 0)
                conflict((dispatch ? "Dispatch" : "Shipment") + " quantity exceeds challan remaining quantity");
        }
    }

    public void maybePostInventoryOnDispatch(Shipment s) {
        String event = settings.findByOrganizationId(org()).map(x -> x.getInventoryDeductionEvent()).orElse("");
        if (!Set.of("DELIVERY_CHALLAN", "CHALLAN_DISPATCH").contains(event.toUpperCase(Locale.ROOT))) return;
        if (s.getFromWarehouseId() == null) conflict("Warehouse is required for inventory deduction");
        for (ShipmentLine line : lines.findByShipmentIdOrderByLineOrder(s.getId())) {
            inventory.postTransaction(new PostTransaction(Type.SALE, line.getProductId(), s.getFromWarehouseId(),
                    BigDecimal.ZERO, line.getQuantity(), "SHIPMENT", s.getId(), s.getShipmentNumber(),
                    "shipment:" + s.getId() + ":" + line.getId(), line.getBatchNumber(), line.getSerialNumber(),
                    null, BigDecimal.ZERO, s.getRemarks(), LocalDate.now()));
        }
    }

    private void apply(Shipment s, ShipmentRequest r) {
        s.setSourceDocumentType(r.sourceDocumentType()); s.setSourceDocumentId(r.sourceDocumentId());
        s.setTransportRequired(r.transportRequired()); s.setTransportMode(r.transportMode()); s.setTransportType(r.transportType());
        s.setTransportCompanyId(r.transportCompanyId()); s.setFromWarehouseId(r.fromWarehouseId());
        s.setShipToPartyType(r.shipToPartyType()); s.setShipToPartyId(r.shipToPartyId()); s.setShipToAddress(r.shipToAddress());
        s.setExpectedDispatchDate(r.expectedDispatchDate()); s.setExpectedDeliveryDate(r.expectedDeliveryDate());
        s.setFreightCharges(z(r.freightCharges())); s.setFreightPaidBy(r.freightPaidBy());
        s.setInsuranceDetails(r.insuranceDetails()); s.setGpsTrackingUrl(r.gpsTrackingUrl());
        s.setEwayBillNumber(r.ewayBillNumber()); s.setEinvoiceReference(r.einvoiceReference()); s.setRemarks(r.remarks());
    }

    private void replaceLines(UUID shipmentId, List<LineRequest> requested) {
        lines.deleteByShipmentId(shipmentId);
        int n = 0;
        for (LineRequest r : requested) {
            ShipmentLine e = new ShipmentLine(); e.setShipmentId(shipmentId); e.setSourceLineId(r.sourceLineId());
            e.setProductId(r.productId()); e.setDescription(r.description()); e.setQuantity(r.quantity()); e.setUnitId(r.unitId());
            e.setBatchNumber(r.batchNumber()); e.setSerialNumber(r.serialNumber()); e.setLineOrder(r.lineOrder() == null ? n++ : r.lineOrder());
            lines.save(e);
        }
    }

    private void replaceLegs(UUID shipmentId, List<LegRequest> requested) {
        legs.deleteByShipmentId(shipmentId);
        int n = 1;
        for (LegRequest r : requested == null ? List.<LegRequest>of() : requested) {
            ShipmentLeg e = new ShipmentLeg(); e.setShipmentId(shipmentId); e.setSequenceNo(r.sequenceNo() == null ? n++ : r.sequenceNo());
            e.setTransportCompanyId(r.transportCompanyId()); e.setVehicleId(r.vehicleId()); e.setDriverId(r.driverId());
            e.setLrNumber(r.lrNumber()); e.setConsignmentNumber(r.consignmentNumber()); e.setVehicleNumberSnapshot(r.vehicleNumberSnapshot());
            e.setDriverNameSnapshot(r.driverNameSnapshot()); e.setDriverMobileSnapshot(r.driverMobileSnapshot());
            e.setExpectedDeparture(r.expectedDeparture()); e.setExpectedArrival(r.expectedArrival()); e.setRemarks(r.remarks()); legs.save(e);
        }
    }

    private void transition(Shipment s, ShipmentStatus status, String remarks, String location, String payload) {
        s.setStatus(status); audit(s, false); shipments.save(s); event(s, status.name(), remarks, location, payload);
        indexShipment(s);
    }
    private void indexShipment(Shipment s) {
        searchEvents.upsert(s.getOrganizationId(), SearchEntityType.SHIPMENT, s.getId());
    }
    private void event(Shipment s, String type, String remarks, String location, String payload) {
        ShipmentEvent e = new ShipmentEvent(); e.setShipmentId(s.getId()); e.setEventType(type); e.setOccurredAt(OffsetDateTime.now());
        e.setActorUserId(TenantContext.userId().orElse(null)); e.setActorType(TenantContext.userId().isPresent() ? ShipmentActorType.USER : ShipmentActorType.SYSTEM);
        e.setRemarks(remarks); e.setLocationJson(location); e.setPayloadJson(payload); events.save(e);
    }
    private Shipment load(UUID id) { return shipments.findByIdAndOrganizationIdAndDeletedFalse(id, org()).orElseThrow(() -> notFound("Shipment not found")); }
    private void requireStatus(Shipment s, ShipmentStatus expected) { if (s.getStatus() != expected) conflict("Expected " + expected + " but shipment is " + s.getStatus()); }
    private Specification<Shipment> baseSpec() { UUID org = org(); return (root, q, cb) -> cb.and(cb.equal(root.get("organizationId"), org), cb.isFalse(root.get("deleted"))); }
    private String nextNumber() {
        var organization = organizations.findById(org()).orElseThrow(() -> notFound("Organization not found"));
        return numbers.next(org(), "SHIPMENT", "SHP", NUMBER_FORMAT, organization.getFinancialYearStart(), LocalDate.now());
    }
    private ShipmentResponse map(Shipment s) {
        List<LegResponse> lr = legs.findByShipmentIdOrderBySequenceNo(s.getId()).stream().map(l -> new LegResponse(l.getId(), l.getSequenceNo(), l.getTransportCompanyId(), l.getVehicleId(), l.getDriverId(), l.getLrNumber(), l.getConsignmentNumber(), l.getVehicleNumberSnapshot(), l.getDriverNameSnapshot(), l.getDriverMobileSnapshot(), l.getExpectedDeparture(), l.getExpectedArrival(), l.getActualDeparture(), l.getActualArrival(), l.getRemarks())).toList();
        List<LineResponse> li = lines.findByShipmentIdOrderByLineOrder(s.getId()).stream().map(l -> new LineResponse(l.getId(), l.getSourceLineId(), l.getProductId(), l.getDescription(), l.getQuantity(), l.getUnitId(), l.getBatchNumber(), l.getSerialNumber(), l.getLineOrder())).toList();
        return new ShipmentResponse(s.getId(), s.getShipmentNumber(), s.getStatus(), s.getSourceDocumentType(), s.getSourceDocumentId(), s.isTransportRequired(), s.getTransportMode(), s.getTransportType(), s.getTransportCompanyId(), s.getFromWarehouseId(), s.getShipToPartyType(), s.getShipToPartyId(), s.getShipToAddress(), s.getExpectedDispatchDate(), s.getExpectedDeliveryDate(), s.getActualDispatchDate(), s.getActualDeliveryDate(), s.getFreightCharges(), s.getFreightPaidBy(), s.getInsuranceDetails(), s.getGpsTrackingUrl(), s.getEwayBillNumber(), s.getEinvoiceReference(), s.getRemarks(), lr, li, s.getVersion());
    }
    private LineRequest request(ShipmentLine l) { return new LineRequest(l.getSourceLineId(), l.getProductId(), l.getDescription(), l.getQuantity(), l.getUnitId(), l.getBatchNumber(), l.getSerialNumber(), l.getLineOrder()); }
    private UUID org() { return TenantContext.getOrganizationId(); }
    private void audit(Shipment s, boolean c) { TenantContext.userId().ifPresent(u -> { if (c) s.setCreatedBy(u); s.setUpdatedBy(u); }); }
    private static boolean text(String s) { return s != null && !s.isBlank(); }
    private static String like(String s) { return "%" + s.toLowerCase(Locale.ROOT) + "%"; }
    private static BigDecimal z(BigDecimal n) { return n == null ? BigDecimal.ZERO : n; }
    private static String remarks(TransitionRequest r) { return r == null ? null : r.remarks(); }
    private static String location(TransitionRequest r) { return r == null ? null : r.locationJson(); }
    private static String payload(TransitionRequest r) { return r == null ? null : r.payloadJson(); }
    private ResponseStatusException notFound(String m) { return new ResponseStatusException(HttpStatus.NOT_FOUND, m); }
    private void conflict(String m) { throw new ResponseStatusException(HttpStatus.CONFLICT, m); }
}
