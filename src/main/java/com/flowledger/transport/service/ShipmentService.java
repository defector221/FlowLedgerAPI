package com.flowledger.transport.service;

import static com.flowledger.transport.dto.TransportDtos.*;

import com.flowledger.common.tenant.TenantContext;
import com.flowledger.common.util.DocumentNumberService;
import com.flowledger.customer.repository.CustomerRepository;
import com.flowledger.inventory.dto.InventoryDtos.PostTransaction;
import com.flowledger.inventory.entity.InventoryTransaction.Type;
import com.flowledger.inventory.service.InventoryService;
import com.flowledger.organization.entity.OrganizationSettings;
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
import com.flowledger.warehouse.repository.WarehouseRepository;
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
    private final CustomerRepository customers;
    private final TransportCompanyRepository companies;
    private final WarehouseRepository warehouses;
    private final TransportActivityNotificationService activityNotifications;

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
            SearchIndexEventPublisher searchEvents,
            CustomerRepository customers,
            TransportCompanyRepository companies,
            WarehouseRepository warehouses,
            TransportActivityNotificationService activityNotifications) {
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
        this.customers = customers;
        this.companies = companies;
        this.warehouses = warehouses;
        this.activityNotifications = activityNotifications;
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
        ensureDefaultLeg(shipment);
        event(shipment, "CREATED", request.remarks(), null, null);
        outbox.enqueue(
                "ShipmentCreated",
                "SHIPMENT",
                shipment.getId(),
                "{\"shipmentNumber\":\"" + shipment.getShipmentNumber() + "\"}");
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
        ensureDefaultLeg(shipment);
        event(shipment, "UPDATED", request.remarks(), null, null);
        shipment = shipments.save(shipment);
        outbox.enqueue(
                "ShipmentUpdated",
                "SHIPMENT",
                shipment.getId(),
                "{\"shipmentNumber\":\"" + shipment.getShipmentNumber() + "\"}");
        indexShipment(shipment);
        return map(shipment);
    }

    public ShipmentResponse createFromChallan(UUID challanId, ChallanShipmentRequest options) {
        DeliveryChallan challan = challans.findDetailedByIdAndOrganizationId(challanId, org())
                .or(() -> challans.findByIdAndOrganizationId(challanId, org()))
                .orElseThrow(() -> notFound("Delivery challan not found"));
        Map<UUID, BigDecimal> reserved = reservedChallanQuantities(challan.getId());
        List<LineRequest> requestedLines = options == null ? null : options.lines();
        List<LineRequest> selected = requestedLines == null || requestedLines.isEmpty()
                ? challan.getItems().stream()
                        .map(i -> {
                            BigDecimal available = availableChallanQuantity(i, reserved);
                            if (available.signum() <= 0) return null;
                            return new LineRequest(
                                    i.getId(),
                                    i.getProductId(),
                                    i.getDescription(),
                                    available,
                                    i.getUnitId(),
                                    null,
                                    null,
                                    i.getLineOrder());
                        })
                        .filter(Objects::nonNull)
                        .toList()
                : requestedLines;
        if (selected.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    reserved.isEmpty()
                            ? "No remaining quantity to ship"
                            : "All challan quantity is already allocated to an open shipment");
        }
        validateChallanLines(challan, selected, false, reserved);
        String shipTo = options != null && text(options.shipToAddress())
                ? options.shipToAddress().trim()
                : resolveCustomerAddress(challan.getCustomerId());
        ShipmentRequest request = new ShipmentRequest(
                "DELIVERY_CHALLAN",
                challan.getId(),
                challan.isTransportRequired(),
                options == null ? null : options.transportMode(),
                options == null ? null : options.transportType(),
                options == null ? null : options.transportCompanyId(),
                challan.getWarehouseId(),
                "CUSTOMER",
                challan.getCustomerId(),
                shipTo,
                options == null ? null : options.expectedDispatchDate(),
                options == null ? null : options.expectedDeliveryDate(),
                options != null && options.freightCharges() != null ? options.freightCharges() : BigDecimal.ZERO,
                options == null ? null : options.freightPaidBy(),
                null,
                null,
                options == null ? null : options.ewayBillNumber(),
                null,
                options != null && text(options.remarks()) ? options.remarks() : challan.getNotes(),
                List.of(),
                selected);
        return create(request);
    }

    public ShipmentResponse updateHeader(UUID id, ShipmentHeaderRequest request) {
        Shipment shipment = load(id);
        if (shipment.getStatus() != ShipmentStatus.DRAFT && shipment.getStatus() != ShipmentStatus.APPROVED) {
            conflict("Header can only be edited in DRAFT or APPROVED status");
        }
        if (request == null) return map(shipment);
        if (request.transportMode() != null) shipment.setTransportMode(request.transportMode());
        if (request.transportType() != null) shipment.setTransportType(request.transportType());
        shipment.setTransportCompanyId(request.transportCompanyId());
        if (request.shipToAddress() != null) shipment.setShipToAddress(request.shipToAddress());
        shipment.setExpectedDispatchDate(request.expectedDispatchDate());
        shipment.setExpectedDeliveryDate(request.expectedDeliveryDate());
        if (request.freightCharges() != null) {
            shipment.setFreightCharges(z(request.freightCharges()));
            shipment.setGrandTotal(z(request.freightCharges())
                    .add(z(shipment.getFuelChargesTotal()))
                    .add(z(shipment.getTollChargesTotal()))
                    .add(z(shipment.getOtherChargesTotal())));
        }
        if (request.freightPaidBy() != null) shipment.setFreightPaidBy(request.freightPaidBy());
        if (request.ewayBillNumber() != null) shipment.setEwayBillNumber(request.ewayBillNumber());
        if (request.remarks() != null) shipment.setRemarks(request.remarks());
        audit(shipment, false);
        shipment = shipments.save(shipment);
        syncDefaultLegHeader(shipment);
        event(shipment, "UPDATED", "Shipment details updated", null, null);
        indexShipment(shipment);
        return map(shipment);
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
        if (isCustomerArranged(shipment)) {
            conflict("Customer-arranged shipments do not require fleet assignment");
        }
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
        if (isCustomerArranged(shipment)) {
            if (shipment.getStatus() != ShipmentStatus.APPROVED
                    && shipment.getStatus() != ShipmentStatus.ASSIGNED
                    && shipment.getStatus() != ShipmentStatus.LOADED) {
                conflict("Expected APPROVED, ASSIGNED, or LOADED but shipment is " + shipment.getStatus());
            }
        } else {
            requireStatus(shipment, ShipmentStatus.LOADED);
        }
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

    public ShipmentResponse addEvent(UUID id, ManualEventRequest request) {
        Shipment shipment = load(id);
        if (shipment.getStatus() == ShipmentStatus.CLOSED || shipment.getStatus() == ShipmentStatus.CANCELLED) {
            conflict("Cannot add timeline events to a closed shipment");
        }
        String type = request == null
                        || request.eventType() == null
                        || request.eventType().isBlank()
                ? "NOTE"
                : request.eventType().trim().toUpperCase(Locale.ROOT);
        event(
                shipment,
                type,
                request == null ? null : request.remarks(),
                request == null ? null : request.locationJson(),
                request == null ? null : request.payloadJson());
        return map(shipment);
    }

    public void recordProviderUpdate(UUID shipmentId, String provider, String status, String payloadJson) {
        Shipment shipment = load(shipmentId);
        event(shipment, "PROVIDER_UPDATE", provider + (status == null ? "" : " · " + status), null, payloadJson);
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
        if (shipment.getStatus() != ShipmentStatus.IN_TRANSIT
                && !(isCustomerArranged(shipment)
                        && (shipment.getStatus() == ShipmentStatus.DISPATCHED
                                || shipment.getStatus() == ShipmentStatus.PARTIALLY_DISPATCHED))) {
            conflict("Expected IN_TRANSIT but shipment is " + shipment.getStatus());
        }
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
            if (text(r.origin())) {
                Subquery<UUID> sq = query.subquery(UUID.class);
                var leg = sq.from(ShipmentLeg.class);
                sq.select(leg.get("shipmentId"))
                        .where(
                                cb.equal(leg.get("shipmentId"), root.get("id")),
                                cb.isFalse(leg.get("deleted")),
                                cb.like(cb.lower(leg.get("originLocation")), like(r.origin())));
                predicates.add(cb.exists(sq));
            }
            if (text(r.destination())) {
                Subquery<UUID> sq = query.subquery(UUID.class);
                var leg = sq.from(ShipmentLeg.class);
                sq.select(leg.get("shipmentId"))
                        .where(
                                cb.equal(leg.get("shipmentId"), root.get("id")),
                                cb.isFalse(leg.get("deleted")),
                                cb.like(cb.lower(leg.get("destinationLocation")), like(r.destination())));
                predicates.add(cb.exists(sq));
            }
            if (r.transportMode() != null) {
                Subquery<UUID> sq = query.subquery(UUID.class);
                var leg = sq.from(ShipmentLeg.class);
                sq.select(leg.get("shipmentId"))
                        .where(
                                cb.equal(leg.get("shipmentId"), root.get("id")),
                                cb.isFalse(leg.get("deleted")),
                                cb.equal(leg.get("transportMode"), r.transportMode()));
                predicates.add(cb.or(cb.equal(root.get("transportMode"), r.transportMode()), cb.exists(sq)));
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
        if (!shipment.isTransportRequired() && !isCustomerArranged(shipment)) return;
        if (shipment.getTransportMode() == null || shipment.getTransportType() == null)
            conflict("Transport mode and type are required");
        if (isCustomerArranged(shipment)) return;
        List<ShipmentLeg> assigned = legs.findByShipmentIdAndDeletedFalseOrderBySequenceNo(shipment.getId());
        if (assigned.isEmpty()) conflict("At least one shipment leg is required");
        boolean invalid = assigned.stream()
                .anyMatch(leg -> leg.getTransportCompanyId() == null
                        || (leg.getVehicleId() == null && !text(leg.getVehicleNumberSnapshot()))
                        || leg.getDriverId() == null);
        if (invalid) conflict("Each leg requires transporter, driver, and vehicle or vehicle number");
    }

    public static boolean isCustomerArranged(Shipment shipment) {
        return shipment.getTransportType() == TransportType.CUSTOMER_ARRANGED
                || shipment.getTransportMode() == TransportMode.CUSTOMER_PICKUP;
    }

    private ShipmentStatus postChallanDispatch(Shipment shipment) {
        if (!"DELIVERY_CHALLAN".equalsIgnoreCase(shipment.getSourceDocumentType())
                || shipment.getSourceDocumentId() == null) return ShipmentStatus.DISPATCHED;
        DeliveryChallan challan = challans.findByIdAndOrganizationId(shipment.getSourceDocumentId(), org())
                .orElseThrow(() -> notFound("Delivery challan not found"));
        List<ShipmentLine> shipmentLines = lines.findByShipmentIdOrderByLineOrder(shipment.getId());
        validateChallanLines(challan, shipmentLines.stream().map(this::request).toList(), true, Map.of());
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

    private static final EnumSet<ShipmentStatus> RESERVES_CHALLAN_QTY = EnumSet.of(
            ShipmentStatus.DRAFT,
            ShipmentStatus.SUBMITTED,
            ShipmentStatus.APPROVED,
            ShipmentStatus.ASSIGNED,
            ShipmentStatus.LOADING,
            ShipmentStatus.LOADED);

    private Map<UUID, BigDecimal> reservedChallanQuantities(UUID challanId) {
        Specification<Shipment> spec = baseSpec()
                .and((root, query, cb) -> cb.and(
                        cb.equal(cb.upper(root.get("sourceDocumentType")), "DELIVERY_CHALLAN"),
                        cb.equal(root.get("sourceDocumentId"), challanId),
                        root.get("status").in(RESERVES_CHALLAN_QTY)));
        Map<UUID, BigDecimal> reserved = new HashMap<>();
        for (Shipment shipment : shipments.findAll(spec)) {
            for (ShipmentLine line : lines.findByShipmentIdOrderByLineOrder(shipment.getId())) {
                if (line.getSourceLineId() == null) continue;
                reserved.merge(line.getSourceLineId(), z(line.getQuantity()), BigDecimal::add);
            }
        }
        return reserved;
    }

    private static BigDecimal availableChallanQuantity(DeliveryChallanItem item, Map<UUID, BigDecimal> reserved) {
        BigDecimal available = item.getQuantityRemaining().subtract(z(reserved.get(item.getId())));
        return available.signum() > 0 ? available : BigDecimal.ZERO;
    }

    private void validateChallanLines(
            DeliveryChallan challan, List<LineRequest> requested, boolean dispatch, Map<UUID, BigDecimal> reserved) {
        Map<UUID, DeliveryChallanItem> byId = new HashMap<>();
        challan.getItems().forEach(i -> byId.put(i.getId(), i));
        for (LineRequest line : requested) {
            DeliveryChallanItem item = byId.get(line.sourceLineId());
            if (item == null || !item.getProductId().equals(line.productId()))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid challan source line");
            BigDecimal available = dispatch ? item.getQuantityRemaining() : availableChallanQuantity(item, reserved);
            if (line.quantity().compareTo(available) > 0)
                conflict((dispatch ? "Dispatch" : "Shipment")
                        + " quantity exceeds challan remaining quantity"
                        + (dispatch ? "" : " (including open shipments)"));
        }
    }

    public void maybePostInventoryOnDispatch(Shipment shipment) {
        String event = settings.findByOrganizationId(org())
                .map(OrganizationSettings::getInventoryDeductionEvent)
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
        Shipment shipment = shipments.findById(shipmentId).orElseThrow(() -> notFound("Shipment not found"));
        int n = 1;
        for (LegRequest r : requested == null ? List.<LegRequest>of() : requested) {
            ShipmentLeg leg = new ShipmentLeg();
            leg.setOrganizationId(shipment.getOrganizationId());
            leg.setShipmentId(shipmentId);
            leg.setSequenceNo(r.sequenceNo() == null ? n++ : r.sequenceNo());
            leg.setStatus(ShipmentLegStatus.READY);
            leg.setTransportMode(r.transportMode() == null ? shipment.getTransportMode() : r.transportMode());
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
            leg.setOriginLocation(r.originLocation());
            leg.setDestinationLocation(r.destinationLocation());
            leg.setWaypointsJson(r.waypointsJson());
            if (r.estimatedDistance() != null) leg.setEstimatedDistance(r.estimatedDistance());
            if (r.estimatedDurationMinutes() != null) leg.setEstimatedDurationMinutes(r.estimatedDurationMinutes());
            if (r.freightCost() != null) leg.setFreightCost(r.freightCost());
            if (r.fuelCost() != null) leg.setFuelCost(r.fuelCost());
            if (r.tollCost() != null) leg.setTollCost(r.tollCost());
            if (r.otherCharges() != null) leg.setOtherCharges(r.otherCharges());
            leg.setDeleted(false);
            TenantContext.userId().ifPresent(u -> {
                leg.setCreatedBy(u);
                leg.setUpdatedBy(u);
            });
            legs.save(leg);
        }
    }

    private void ensureDefaultLeg(Shipment shipment) {
        if (!legs.findByShipmentIdAndDeletedFalseOrderBySequenceNo(shipment.getId())
                .isEmpty()) return;
        ShipmentLeg leg = new ShipmentLeg();
        leg.setOrganizationId(shipment.getOrganizationId());
        leg.setShipmentId(shipment.getId());
        leg.setSequenceNo(1);
        leg.setStatus(ShipmentLegStatus.PLANNED);
        leg.setTransportMode(shipment.getTransportMode());
        leg.setTransportCompanyId(shipment.getTransportCompanyId());
        leg.setOriginLocation(resolveWarehouseLabel(shipment.getFromWarehouseId()));
        leg.setDestinationLocation(
                text(shipment.getShipToAddress())
                        ? shipment.getShipToAddress()
                        : resolveCustomerName(shipment.getShipToPartyId()));
        leg.setExpectedDeparture(shipment.getExpectedDispatchDate());
        leg.setExpectedArrival(shipment.getExpectedDeliveryDate());
        leg.setFreightCost(z(shipment.getFreightCharges()));
        leg.setRemarks(shipment.getRemarks());
        leg.setDeleted(false);
        TenantContext.userId().ifPresent(u -> {
            leg.setCreatedBy(u);
            leg.setUpdatedBy(u);
        });
        legs.save(leg);
    }

    private void syncDefaultLegHeader(Shipment shipment) {
        List<ShipmentLeg> existing = legs.findByShipmentIdAndDeletedFalseOrderBySequenceNo(shipment.getId());
        if (existing.size() != 1) return;
        ShipmentLeg leg = existing.get(0);
        if (leg.getStatus() != ShipmentLegStatus.PLANNED && leg.getStatus() != ShipmentLegStatus.READY) return;
        leg.setTransportMode(shipment.getTransportMode());
        leg.setTransportCompanyId(shipment.getTransportCompanyId());
        String origin = resolveWarehouseLabel(shipment.getFromWarehouseId());
        String destination = text(shipment.getShipToAddress())
                ? shipment.getShipToAddress()
                : resolveCustomerName(shipment.getShipToPartyId());
        if (!text(leg.getOriginLocation()) && text(origin)) leg.setOriginLocation(origin);
        if (!text(leg.getDestinationLocation()) && text(destination)) leg.setDestinationLocation(destination);
        leg.setExpectedDeparture(shipment.getExpectedDispatchDate());
        leg.setExpectedArrival(shipment.getExpectedDeliveryDate());
        leg.setFreightCost(z(shipment.getFreightCharges()));
        auditLeg(leg);
        legs.save(leg);
    }

    private void auditLeg(ShipmentLeg leg) {
        TenantContext.userId().ifPresent(leg::setUpdatedBy);
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
        activityNotifications.notifyShipmentEvent(shipment, type, remarks);
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
        List<LegResponse> lr = legs.findByShipmentIdAndDeletedFalseOrderBySequenceNo(shipment.getId()).stream()
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
                        leg.getGpsProvider()))
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
                shipment.getVersion(),
                shipment.getPriority(),
                shipment.getTotalDistance(),
                shipment.getFuelChargesTotal(),
                shipment.getTollChargesTotal(),
                shipment.getOtherChargesTotal(),
                shipment.getGrandTotal(),
                resolveCompanyName(shipment.getTransportCompanyId()),
                resolveCustomerName(shipment.getShipToPartyId()),
                resolveWarehouseLabel(shipment.getFromWarehouseId()));
    }

    private String resolveCompanyName(UUID id) {
        if (id == null) return null;
        return companies
                .findByIdAndOrganizationIdAndDeletedFalse(id, org())
                .map(TransportCompany::getName)
                .orElse(null);
    }

    private String resolveCustomerName(UUID id) {
        if (id == null) return null;
        return customers
                .findByIdAndOrganizationId(id, org())
                .map(c -> {
                    if (text(c.getCompanyName())) return c.getCompanyName();
                    return c.getCustomerName();
                })
                .orElse(null);
    }

    private String resolveCustomerAddress(UUID id) {
        if (id == null) return null;
        return customers
                .findByIdAndOrganizationId(id, org())
                .map(c -> {
                    if (text(c.getShippingAddress())) return c.getShippingAddress();
                    return c.getBillingAddress();
                })
                .orElse(null);
    }

    private String resolveWarehouseLabel(UUID id) {
        if (id == null) return null;
        return warehouses
                .findByIdAndOrganizationId(id, org())
                .map(w -> text(w.getWarehouseName()) ? w.getWarehouseName() : w.getWarehouseCode())
                .orElse(null);
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
