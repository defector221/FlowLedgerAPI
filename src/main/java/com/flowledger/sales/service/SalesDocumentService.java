package com.flowledger.sales.service;

import com.flowledger.ai.workflow.AiWorkflowGateService;
import com.flowledger.common.dto.PageResponse;
import com.flowledger.common.tenant.TenantContext;
import com.flowledger.common.util.DocumentNumberService;
import com.flowledger.customer.entity.Customer;
import com.flowledger.customer.repository.CustomerRepository;
import com.flowledger.finance.voucher.adapter.DocumentVoucherFacade;
import com.flowledger.inventory.allocation.AllocationMode;
import com.flowledger.inventory.allocation.InventoryDeductionCoordinator;
import com.flowledger.inventory.allocation.ReservationResult;
import com.flowledger.inventory.dto.InventoryDtos.PostTransaction;
import com.flowledger.inventory.entity.InventoryTransaction.Type;
import com.flowledger.inventory.service.InventoryService;
import com.flowledger.organization.entity.Organization;
import com.flowledger.organization.repository.OrganizationRepository;
import com.flowledger.product.entity.Product;
import com.flowledger.product.repository.ProductRepository;
import com.flowledger.sales.dto.SalesAllocationDtos.ConfirmLineAllocationRequest;
import com.flowledger.sales.dto.SalesAllocationDtos.ConfirmOrderRequest;
import com.flowledger.sales.dto.SalesAllocationDtos.ConvertToChallanLineRequest;
import com.flowledger.sales.dto.SalesAllocationDtos.ConvertToChallanRequest;
import com.flowledger.sales.dto.SalesDtos.*;
import com.flowledger.sales.entity.*;
import com.flowledger.sales.repository.*;
import com.flowledger.tax.TaxSplitDefaults;
import com.flowledger.tax.dto.GstCalculationDtos;
import com.flowledger.tax.service.GstCalculationService;
import com.flowledger.transport.domain.TransportEnums.ShipmentStatus;
import com.flowledger.transport.repository.ShipmentRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SalesDocumentService {

    private static final String NUMBER_FORMAT = "{PREFIX}/{FY}/{SEQ:6}";

    private final QuotationRepository quotations;
    private final SalesOrderRepository orders;
    private final DeliveryChallanRepository challans;
    private final SalesInvoiceRepository invoices;
    private final SalesReturnRepository returns;
    private final CreditNoteRepository creditNotes;
    private final OrganizationRepository organizations;
    private final CustomerRepository customers;
    private final ProductRepository products;
    private final DocumentNumberService numbers;
    private final SalesInvoiceService invoiceService;
    private final GstCalculationService gst;
    private final InventoryService inventory;
    private final DocumentVoucherFacade documentPosting;
    private final ObjectProvider<AiWorkflowGateService> workflowGate;
    private final ShipmentRepository shipments;
    private final InventoryDeductionCoordinator inventoryCoordinator;

    private static final EnumSet<ShipmentStatus> DISPATCHED_OR_LATER = EnumSet.of(
            ShipmentStatus.PARTIALLY_DISPATCHED,
            ShipmentStatus.DISPATCHED,
            ShipmentStatus.IN_TRANSIT,
            ShipmentStatus.DELIVERED,
            ShipmentStatus.CLOSED);

    public SalesDocumentService(
            QuotationRepository quotations,
            SalesOrderRepository orders,
            DeliveryChallanRepository challans,
            SalesInvoiceRepository invoices,
            SalesReturnRepository returns,
            CreditNoteRepository creditNotes,
            OrganizationRepository organizations,
            CustomerRepository customers,
            ProductRepository products,
            DocumentNumberService numbers,
            SalesInvoiceService invoiceService,
            GstCalculationService gst,
            InventoryService inventory,
            DocumentVoucherFacade documentPosting,
            ObjectProvider<AiWorkflowGateService> workflowGate,
            ShipmentRepository shipments,
            InventoryDeductionCoordinator inventoryCoordinator) {
        this.quotations = quotations;
        this.orders = orders;
        this.challans = challans;
        this.invoices = invoices;
        this.returns = returns;
        this.creditNotes = creditNotes;
        this.organizations = organizations;
        this.customers = customers;
        this.products = products;
        this.numbers = numbers;
        this.invoiceService = invoiceService;
        this.gst = gst;
        this.inventory = inventory;
        this.documentPosting = documentPosting;
        this.workflowGate = workflowGate;
        this.shipments = shipments;
        this.inventoryCoordinator = inventoryCoordinator;
    }

    private void gate(String documentType, UUID entityId, BigDecimal amount, String action) {
        AiWorkflowGateService gate = workflowGate.getIfAvailable();
        if (gate != null) {
            gate.requireApproved(documentType, entityId, amount, action);
        }
    }

    // ── Quotations ──────────────────────────────────────────────────────────

    @Transactional
    public Quotation createQuotation(QuotationRequest request) {
        Organization org = organization();
        Quotation quotation = new Quotation();
        quotation.setOrganizationId(orgId());
        quotation.setStatus(Quotation.Status.DRAFT);
        applyQuotation(quotation, request, org);
        quotation.setQuotationNumber(numbers.next(
                org.getId(),
                "QUOTATION",
                org.getQuotationPrefix(),
                NUMBER_FORMAT,
                org.getFinancialYearStart(),
                quotation.getQuotationDate()));
        return quotations.save(quotation);
    }

    @Transactional
    public Quotation updateQuotation(UUID id, QuotationRequest request) {
        Quotation quotation = getQuotation(id);
        if (quotation.getStatus() != Quotation.Status.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only draft quotations can be updated");
        }
        applyQuotation(quotation, request, organization());
        return quotations.save(quotation);
    }

    @Transactional(readOnly = true)
    public PageResponse<Quotation> listQuotations(Pageable pageable) {
        return PageResponse.from(quotations.findByOrganizationIdOrderByQuotationDateDesc(orgId(), pageable));
    }

    @Transactional(readOnly = true)
    public Quotation getQuotation(UUID id) {
        Quotation quotation = quotations
                .findDetailedByIdAndOrganizationId(id, orgId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Quotation not found"));
        for (QuotationItem item : quotation.getItems()) {
            if (item.getDescription() == null || item.getDescription().isBlank()) {
                item.setDescription(resolveLineDescription(item.getProductId(), item.getDescription()));
            }
        }
        fillPartyDefaults(quotation);
        return quotation;
    }

    @Transactional
    public Quotation cancelQuotation(UUID id) {
        Quotation quotation = getQuotation(id);
        if (quotation.getStatus() == Quotation.Status.CANCELLED) {
            return quotation;
        }
        if (quotation.getStatus() == Quotation.Status.CONVERTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Converted quotation cannot be cancelled");
        }
        quotation.setStatus(Quotation.Status.CANCELLED);
        return quotations.save(quotation);
    }

    @Transactional
    public SalesOrder convertQuotationToOrder(UUID quotationId) {
        Quotation quotation = getQuotation(quotationId);
        if (quotation.getConvertedToOrderId() != null) {
            return orders.findByIdAndOrganizationId(quotation.getConvertedToOrderId(), orgId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Converted order missing"));
        }
        if (quotation.getStatus() == Quotation.Status.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cancelled quotation cannot be converted");
        }
        gate("QUOTATION", quotation.getId(), quotation.getGrandTotal(), "convert to sales order");
        Organization org = organization();
        SalesOrder order = new SalesOrder();
        order.setOrganizationId(orgId());
        order.setCustomerId(quotation.getCustomerId());
        order.setOrderDate(LocalDate.now());
        order.setQuotationId(quotation.getId());
        order.setBillingAddress(quotation.getBillingAddress());
        order.setShippingAddress(quotation.getShippingAddress());
        order.setPlaceOfSupply(quotation.getPlaceOfSupply());
        order.setTermsAndConditions(quotation.getTermsAndConditions());
        order.setNotes(quotation.getNotes());
        fillPartyDefaults(order);
        order.setOrderNumber(numbers.next(
                org.getId(),
                "SALES_ORDER",
                org.getSalesOrderPrefix(),
                NUMBER_FORMAT,
                org.getFinancialYearStart(),
                order.getOrderDate()));
        order.setSubtotal(quotation.getSubtotal());
        order.setDiscountTotal(quotation.getDiscountTotal());
        order.setTaxTotal(quotation.getTaxTotal());
        order.setGrandTotal(quotation.getGrandTotal());
        order.setStatus(SalesOrder.Status.CONFIRMED);
        int i = 0;
        for (QuotationItem quotationItem : quotation.getItems()) {
            SalesOrderItem item = new SalesOrderItem();
            item.setSalesOrder(order);
            item.setProductId(quotationItem.getProductId());
            item.setDescription(quotationItem.getDescription());
            item.setHsnSacCode(quotationItem.getHsnSacCode());
            item.setQuantity(quotationItem.getQuantity());
            item.setUnitId(quotationItem.getUnitId());
            item.setRate(quotationItem.getRate());
            item.setDiscountPercent(quotationItem.getDiscountPercent());
            item.setDiscountAmount(quotationItem.getDiscountAmount());
            item.setTaxRate(quotationItem.getTaxRate());
            item.setTaxType(
                    quotationItem.getTaxType() == null
                                    || quotationItem.getTaxType().isBlank()
                            ? "GST"
                            : quotationItem.getTaxType());
            item.setSplitStrategy(
                    quotationItem.getSplitStrategy() == null
                                    || quotationItem.getSplitStrategy().isBlank()
                            ? "PLACE_OF_SUPPLY"
                            : quotationItem.getSplitStrategy());
            item.setCgstSharePercent(
                    quotationItem.getCgstSharePercent() == null
                            ? new BigDecimal("50")
                            : quotationItem.getCgstSharePercent());
            item.setSgstSharePercent(
                    quotationItem.getSgstSharePercent() == null
                            ? new BigDecimal("50")
                            : quotationItem.getSgstSharePercent());
            item.setTaxableAmount(quotationItem.getTaxableAmount());
            item.setCgstAmount(quotationItem.getCgstAmount());
            item.setSgstAmount(quotationItem.getSgstAmount());
            item.setIgstAmount(quotationItem.getIgstAmount());
            item.setLineTotal(quotationItem.getLineTotal());
            item.setLineOrder(i++);
            order.getItems().add(item);
        }
        SalesOrder saved = orders.save(order);
        quotation.setConvertedToOrderId(saved.getId());
        quotation.setStatus(Quotation.Status.CONVERTED);
        quotations.save(quotation);
        if (inventoryCoordinator.shouldReserveOnSalesOrderConfirm(orgId())) {
            UUID warehouseId = inventoryCoordinator.resolveWarehouseId(orgId(), saved.getWarehouseId());
            saved.setWarehouseId(warehouseId);
            reserveOrderLines(saved, warehouseId, null);
            saved = orders.save(saved);
        }
        return saved;
    }

    // ── Sales orders ────────────────────────────────────────────────────────

    @Transactional
    public SalesOrder createOrder(OrderRequest request) {
        Organization org = organization();
        SalesOrder order = new SalesOrder();
        order.setOrganizationId(orgId());
        order.setStatus(SalesOrder.Status.DRAFT);
        applyOrder(order, request, org);
        order.setOrderNumber(numbers.next(
                org.getId(),
                "SALES_ORDER",
                org.getSalesOrderPrefix(),
                NUMBER_FORMAT,
                org.getFinancialYearStart(),
                order.getOrderDate()));
        return orders.save(order);
    }

    @Transactional
    public SalesOrder updateOrder(UUID id, OrderRequest request) {
        SalesOrder order = getOrder(id);
        if (order.getStatus() != SalesOrder.Status.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only draft orders can be updated");
        }
        applyOrder(order, request, organization());
        return orders.save(order);
    }

    @Transactional(readOnly = true)
    public PageResponse<SalesOrder> listOrders(Pageable pageable) {
        return PageResponse.from(orders.findByOrganizationIdOrderByOrderDateDesc(orgId(), pageable));
    }

    @Transactional(readOnly = true)
    public SalesOrder getOrder(UUID id) {
        SalesOrder order = orders.findDetailedByIdAndOrganizationId(id, orgId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sales order not found"));
        for (SalesOrderItem item : order.getItems()) {
            if (item.getDescription() == null || item.getDescription().isBlank()) {
                item.setDescription(resolveLineDescription(item.getProductId(), item.getDescription()));
            }
        }
        fillPartyDefaults(order);
        return order;
    }

    @Transactional
    public SalesOrder cancelOrder(UUID id) {
        SalesOrder order = getOrder(id);
        if (order.getStatus() == SalesOrder.Status.CANCELLED) {
            return order;
        }
        if (order.getStatus() == SalesOrder.Status.FULFILLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Fulfilled order cannot be cancelled");
        }
        inventoryCoordinator.releaseSalesOrder(orgId(), id);
        order.setStatus(SalesOrder.Status.CANCELLED);
        return orders.save(order);
    }

    @Transactional
    public SalesOrder confirmOrder(UUID id, ConfirmOrderRequest request) {
        SalesOrder order = getOrder(id);
        if (order.getStatus() != SalesOrder.Status.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only draft orders can be confirmed");
        }
        UUID warehouseId = inventoryCoordinator.resolveWarehouseId(orgId(), request == null ? null : request.warehouseId());
        order.setWarehouseId(warehouseId);
        if (inventoryCoordinator.shouldReserveOnSalesOrderConfirm(orgId())) {
            reserveOrderLines(order, warehouseId, null);
        }
        order.setStatus(SalesOrder.Status.CONFIRMED);
        return orders.save(order);
    }

    @Transactional
    public SalesOrder confirmOrderLineAllocation(UUID orderId, UUID lineId, ConfirmLineAllocationRequest request) {
        SalesOrder order = getOrder(orderId);
        if (order.getStatus() != SalesOrder.Status.DRAFT && order.getStatus() != SalesOrder.Status.CONFIRMED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order cannot be updated");
        }
        SalesOrderItem line = order.getItems().stream()
                .filter(i -> i.getId().equals(lineId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Line not found"));
        UUID warehouseId = order.getWarehouseId() != null
                ? order.getWarehouseId()
                : inventoryCoordinator.resolveWarehouseId(orgId(), null);
        order.setWarehouseId(warehouseId);
        AllocationMode mode = parseAllocationMode(request.allocationMode());
        ReservationResult result = inventoryCoordinator.reserveSalesOrderLine(
                orgId(),
                line.getProductId(),
                warehouseId,
                line.getQuantity(),
                request.batchId(),
                mode != null ? mode : AllocationMode.BATCH_MANUAL,
                orderId,
                lineId);
        applyReservationToLine(line, warehouseId, result);
        if (order.getStatus() == SalesOrder.Status.DRAFT) {
            order.setStatus(SalesOrder.Status.CONFIRMED);
        }
        return orders.save(order);
    }

    private void reserveOrderLines(SalesOrder order, UUID warehouseId, UUID onlyLineId) {
        for (SalesOrderItem line : order.getItems()) {
            if (onlyLineId != null && !onlyLineId.equals(line.getId())) {
                continue;
            }
            ReservationResult result = inventoryCoordinator.reserveSalesOrderLine(
                    orgId(),
                    line.getProductId(),
                    warehouseId,
                    line.getQuantity(),
                    line.getInventoryBatchId(),
                    parseAllocationMode(line.getAllocationMode()),
                    order.getId(),
                    line.getId());
            applyReservationToLine(line, warehouseId, result);
        }
    }

    private void applyReservationToLine(SalesOrderItem line, UUID warehouseId, ReservationResult result) {
        if (result.reservationId() == null && result.allocated() == null) {
            return;
        }
        line.setWarehouseId(warehouseId);
        line.setStockReservationId(result.reservationId());
        if (result.allocated() != null) {
            line.setInventoryBatchId(result.allocated().batchId());
            line.setAllocationMode(result.allocated().allocationMode().name());
        }
    }

    private AllocationMode parseAllocationMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return null;
        }
        try {
            return AllocationMode.valueOf(mode);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    @Transactional
    public DeliveryChallan convertOrderToChallan(UUID orderId, ConvertToChallanRequest request) {
        SalesOrder order = getOrder(orderId);
        if (order.getStatus() == SalesOrder.Status.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cancelled order cannot be converted");
        }
        gate("SALES_ORDER", order.getId(), order.getGrandTotal(), "convert to delivery challan");
        Organization org = organization();
        UUID warehouseId = inventoryCoordinator.resolveWarehouseId(
                orgId(), request == null ? null : request.warehouseId());
        if (order.getWarehouseId() == null) {
            order.setWarehouseId(warehouseId);
        } else if (request != null && request.warehouseId() != null) {
            warehouseId = request.warehouseId();
        } else {
            warehouseId = order.getWarehouseId();
        }

        Map<UUID, BigDecimal> requestedByLine = buildChallanQtyMap(order, request);
        DeliveryChallan challan = new DeliveryChallan();
        challan.setOrganizationId(orgId());
        challan.setCustomerId(order.getCustomerId());
        challan.setSalesOrderId(order.getId());
        challan.setWarehouseId(warehouseId);
        challan.setChallanDate(LocalDate.now());
        challan.setStatus(DeliveryChallan.Status.DELIVERED);
        challan.setChallanNumber(numbers.next(
                org.getId(),
                "DELIVERY_CHALLAN",
                org.getDeliveryChallanPrefix(),
                NUMBER_FORMAT,
                org.getFinancialYearStart(),
                challan.getChallanDate()));

        int i = 0;
        for (SalesOrderItem orderItem : order.getItems()) {
            BigDecimal qty = requestedByLine.get(orderItem.getId());
            if (qty == null || qty.signum() <= 0) {
                continue;
            }
            BigDecimal delivered = challans.sumDeliveredQtyForOrderLine(orgId(), orderId, orderItem.getId());
            BigDecimal remaining = orderItem.getQuantity().subtract(delivered);
            if (qty.compareTo(remaining) > 0) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Challan quantity exceeds remaining order quantity for line "
                                + orderItem.getId());
            }
            DeliveryChallanItem item = new DeliveryChallanItem();
            item.setDeliveryChallan(challan);
            item.setSalesOrderItemId(orderItem.getId());
            item.setProductId(orderItem.getProductId());
            item.setDescription(resolveLineDescription(orderItem.getProductId(), orderItem.getDescription()));
            item.setQuantity(qty);
            item.setUnitId(orderItem.getUnitId());
            item.setLineOrder(i++);
            challan.getItems().add(item);
        }
        if (challan.getItems().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No challan lines to deliver");
        }

        DeliveryChallan saved = challans.save(challan);
        for (DeliveryChallanItem item : saved.getItems()) {
            SalesOrderItem orderItem = order.getItems().stream()
                    .filter(line -> line.getId().equals(item.getSalesOrderItemId()))
                    .findFirst()
                    .orElseThrow();
            inventoryCoordinator.transferOrderLineToChallan(
                    orgId(), orderItem, item, saved.getId(), item.getId(), item.getQuantity());
        }
        orders.save(order);
        return challans.save(saved);
    }

    private Map<UUID, BigDecimal> buildChallanQtyMap(SalesOrder order, ConvertToChallanRequest request) {
        Map<UUID, BigDecimal> map = new HashMap<>();
        if (request != null && request.lines() != null && !request.lines().isEmpty()) {
            for (ConvertToChallanLineRequest line : request.lines()) {
                map.put(line.orderLineId(), line.quantity());
            }
            return map;
        }
        for (SalesOrderItem orderItem : order.getItems()) {
            BigDecimal delivered = challans.sumDeliveredQtyForOrderLine(orgId(), order.getId(), orderItem.getId());
            BigDecimal remaining = orderItem.getQuantity().subtract(delivered);
            if (remaining.signum() > 0) {
                map.put(orderItem.getId(), remaining);
            }
        }
        return map;
    }

    @Transactional
    public SalesInvoice convertOrderToInvoice(UUID orderId, UUID warehouseId) {
        SalesOrder order = getOrder(orderId);
        if (order.getStatus() == SalesOrder.Status.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cancelled order cannot be converted");
        }
        gate("SALES_ORDER", order.getId(), order.getGrandTotal(), "convert to invoice");
        return invoices.findByOrganizationIdAndSalesOrderIdAndDeliveryChallanIdIsNull(orgId(), orderId)
                .orElseGet(() -> createInvoiceFromOrder(order, warehouseId, null));
    }

    // ── Delivery challans ───────────────────────────────────────────────────

    @Transactional
    public DeliveryChallan createChallan(ChallanRequest request) {
        Organization org = organization();
        DeliveryChallan challan = new DeliveryChallan();
        challan.setOrganizationId(orgId());
        challan.setStatus(DeliveryChallan.Status.DRAFT);
        applyChallan(challan, request);
        challan.setChallanNumber(numbers.next(
                org.getId(),
                "DELIVERY_CHALLAN",
                org.getDeliveryChallanPrefix(),
                NUMBER_FORMAT,
                org.getFinancialYearStart(),
                challan.getChallanDate()));
        return challans.save(challan);
    }

    @Transactional
    public DeliveryChallan updateChallan(UUID id, ChallanRequest request) {
        DeliveryChallan challan = getChallan(id);
        if (challan.getStatus() != DeliveryChallan.Status.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only draft challans can be updated");
        }
        applyChallan(challan, request);
        return challans.save(challan);
    }

    @Transactional
    public DeliveryChallan updateChallanTransportRequired(UUID id, boolean transportRequired) {
        DeliveryChallan challan = getChallan(id);
        if (challan.getStatus() == DeliveryChallan.Status.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cancelled challan cannot be updated");
        }
        if (!transportRequired && hasDispatchedShipment(challan.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Transport required cannot be turned off after a shipment has been dispatched");
        }
        challan.setTransportRequired(transportRequired);
        return challans.save(challan);
    }

    private boolean hasDispatchedShipment(UUID challanId) {
        return shipments
                .findAll((root, query, cb) -> cb.and(
                        cb.equal(root.get("organizationId"), orgId()),
                        cb.isFalse(root.get("deleted")),
                        cb.equal(cb.upper(root.get("sourceDocumentType")), "DELIVERY_CHALLAN"),
                        cb.equal(root.get("sourceDocumentId"), challanId),
                        root.get("status").in(DISPATCHED_OR_LATER)))
                .stream()
                .findAny()
                .isPresent();
    }

    @Transactional(readOnly = true)
    public PageResponse<DeliveryChallan> listChallans(Pageable pageable) {
        return PageResponse.from(challans.findByOrganizationIdOrderByChallanDateDesc(orgId(), pageable));
    }

    @Transactional(readOnly = true)
    public DeliveryChallan getChallan(UUID id) {
        DeliveryChallan challan = challans.findDetailedByIdAndOrganizationId(id, orgId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Challan not found"));
        for (DeliveryChallanItem item : challan.getItems()) {
            if (item.getDescription() == null || item.getDescription().isBlank()) {
                item.setDescription(resolveLineDescription(item.getProductId(), item.getDescription()));
            }
        }
        invoices.findByOrganizationIdAndDeliveryChallanId(orgId(), challan.getId())
                .ifPresent(invoice -> {
                    challan.setLinkedInvoiceId(invoice.getId());
                    challan.setLinkedInvoiceNumber(invoice.getInvoiceNumber());
                });
        return challan;
    }

    @Transactional
    public DeliveryChallan cancelChallan(UUID id) {
        DeliveryChallan challan = getChallan(id);
        if (challan.getStatus() == DeliveryChallan.Status.CANCELLED) {
            return challan;
        }
        challan.setStatus(DeliveryChallan.Status.CANCELLED);
        return challans.save(challan);
    }

    @Transactional(readOnly = true)
    public InvoiceDetail getInvoiceForChallan(UUID challanId) {
        getChallan(challanId); // ensure tenant + exists
        SalesInvoice invoice = invoices.findByOrganizationIdAndDeliveryChallanId(orgId(), challanId)
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No invoice linked to this challan"));
        return invoiceService.get(invoice.getId());
    }

    @Transactional
    public SalesInvoice convertChallanToInvoice(UUID challanId) {
        DeliveryChallan challan = getChallan(challanId);
        if (challan.getStatus() == DeliveryChallan.Status.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cancelled challan cannot be converted");
        }
        var existing = invoices.findByOrganizationIdAndDeliveryChallanId(orgId(), challanId);
        if (existing.isPresent()) {
            return existing.get();
        }
        SalesOrder order = challan.getSalesOrderId() == null
                ? null
                : orders.findByIdAndOrganizationId(challan.getSalesOrderId(), orgId())
                        .orElse(null);
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Challan has no linked sales order");
        }
        // Gate on the challan (source doc), same pattern as SO → DC — not on a draft invoice id.
        gate("DELIVERY_CHALLAN", challan.getId(), order.getGrandTotal(), "convert to invoice");
        return createInvoiceFromOrder(order, challan.getWarehouseId(), challan.getId());
    }

    // ── Sales returns ───────────────────────────────────────────────────────

    @Transactional
    public SalesReturn createReturn(ReturnRequest request) {
        SalesInvoice invoice = invoices.findByIdAndOrganizationId(request.salesInvoiceId(), orgId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found"));
        if (invoice.getStatus() == SalesInvoice.Status.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot return a cancelled invoice");
        }
        Organization org = organization();
        SalesReturn salesReturn = new SalesReturn();
        salesReturn.setOrganizationId(orgId());
        salesReturn.setSalesInvoiceId(invoice.getId());
        salesReturn.setCustomerId(invoice.getCustomerId());
        salesReturn.setReturnDate(request.returnDate() == null ? LocalDate.now() : request.returnDate());
        salesReturn.setStatus("DRAFT");
        salesReturn.setNotes(request.notes());
        salesReturn.setReturnNumber(numbers.next(
                org.getId(),
                "SALES_RETURN",
                "SR",
                NUMBER_FORMAT,
                org.getFinancialYearStart(),
                salesReturn.getReturnDate()));
        BigDecimal total = BigDecimal.ZERO;
        int i = 0;
        for (ReturnItem returnItem : request.items()) {
            SalesReturnItem item = new SalesReturnItem();
            item.setSalesReturn(salesReturn);
            item.setProductId(returnItem.productId());
            item.setQuantity(returnItem.quantity());
            item.setRate(returnItem.rate());
            item.setLineTotal(returnItem.quantity().multiply(returnItem.rate()).setScale(2, RoundingMode.HALF_UP));
            item.setLineOrder(i++);
            total = total.add(item.getLineTotal());
            salesReturn.getItems().add(item);
        }
        salesReturn.setGrandTotal(total);
        return returns.save(salesReturn);
    }

    @Transactional
    public SalesReturn confirmReturn(UUID id) {
        SalesReturn salesReturn = getReturn(id);
        if ("CANCELLED".equals(salesReturn.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cancelled return cannot be confirmed");
        }
        if ("CONFIRMED".equals(salesReturn.getStatus()) && salesReturn.isInventoryPosted()) {
            return salesReturn;
        }
        SalesInvoice invoice = invoices.findByIdAndOrganizationId(salesReturn.getSalesInvoiceId(), orgId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found"));
        if (invoice.getWarehouseId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invoice has no warehouse for stock return");
        }
        if (!salesReturn.isInventoryPosted()) {
            for (SalesReturnItem line : salesReturn.getItems()) {
                inventory.postTransaction(new PostTransaction(
                        Type.SALES_RETURN,
                        line.getProductId(),
                        invoice.getWarehouseId(),
                        line.getQuantity(),
                        BigDecimal.ZERO,
                        "SALES_RETURN",
                        salesReturn.getId(),
                        salesReturn.getReturnNumber(),
                        "sales-return:" + salesReturn.getId() + ":" + line.getId(),
                        null,
                        null,
                        null,
                        null,
                        salesReturn.getNotes(),
                        salesReturn.getReturnDate()));
            }
            salesReturn.setInventoryPosted(true);
        }
        salesReturn.setStatus("CONFIRMED");
        SalesReturn saved = returns.save(salesReturn);
        documentPosting.postSalesReturn(saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public SalesReturn getReturn(UUID id) {
        SalesReturn salesReturn = returns.findByIdAndOrganizationId(id, orgId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sales return not found"));
        salesReturn.getItems().size();
        return salesReturn;
    }

    @Transactional(readOnly = true)
    public PageResponse<SalesReturn> listReturns(Pageable pageable) {
        return PageResponse.from(returns.findByOrganizationIdOrderByReturnDateDesc(orgId(), pageable));
    }

    // ── Credit notes ────────────────────────────────────────────────────────

    @Transactional
    public CreditNote createCreditNote(CreditNoteRequest request) {
        if (request.salesReturnId() == null && request.salesInvoiceId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "salesReturnId or salesInvoiceId is required");
        }
        if (request.salesReturnId() != null) {
            getReturn(request.salesReturnId());
        }
        if (request.salesInvoiceId() != null) {
            invoices.findByIdAndOrganizationId(request.salesInvoiceId(), orgId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found"));
        }
        Organization org = organization();
        CreditNote creditNote = new CreditNote();
        creditNote.setOrganizationId(orgId());
        creditNote.setCustomerId(request.customerId());
        creditNote.setSalesReturnId(request.salesReturnId());
        creditNote.setSalesInvoiceId(request.salesInvoiceId());
        creditNote.setCreditNoteDate(request.creditNoteDate() == null ? LocalDate.now() : request.creditNoteDate());
        creditNote.setAmount(request.amount());
        creditNote.setNotes(request.notes());
        creditNote.setStatus("ISSUED");
        creditNote.setCreditNoteNumber(numbers.next(
                org.getId(),
                "CREDIT_NOTE",
                "CN",
                NUMBER_FORMAT,
                org.getFinancialYearStart(),
                creditNote.getCreditNoteDate()));
        CreditNote saved = creditNotes.save(creditNote);
        documentPosting.postCreditNote(saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public PageResponse<CreditNote> listCreditNotes(Pageable pageable) {
        return PageResponse.from(creditNotes.findByOrganizationIdOrderByCreditNoteDateDesc(orgId(), pageable));
    }

    @Transactional(readOnly = true)
    public CreditNote getCreditNote(UUID id) {
        return creditNotes
                .findByIdAndOrganizationId(id, orgId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Credit note not found"));
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private SalesInvoice createInvoiceFromOrder(SalesOrder order, UUID warehouseId, UUID challanId) {
        DeliveryChallan challan = challanId == null
                ? null
                : challans.findDetailedByIdAndOrganizationId(challanId, orgId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Challan not found"));
        var requestItems = (challan != null ? challan.getItems() : order.getItems()).stream()
                .map(sourceLine -> {
                    SalesOrderItem orderLine = resolveOrderLineForInvoice(order, challan, sourceLine);
                    return new Item(
                            orderLine.getProductId(),
                            orderLine.getDescription(),
                            orderLine.getHsnSacCode(),
                            sourceLine instanceof DeliveryChallanItem dcLine
                                    ? dcLine.getQuantity()
                                    : orderLine.getQuantity(),
                            orderLine.getUnitId(),
                            orderLine.getRate(),
                            orderLine.getDiscountPercent(),
                            orderLine.getTaxRate(),
                            orderLine.getTaxType(),
                            orderLine.getSplitStrategy(),
                            orderLine.getCgstSharePercent(),
                            orderLine.getSgstSharePercent());
                })
                .toList();
        var request = new Invoice(
                order.getCustomerId(),
                LocalDate.now(),
                LocalDate.now().plusDays(30),
                warehouseId,
                order.getId(),
                challanId,
                order.getBillingAddress(),
                order.getShippingAddress(),
                order.getPlaceOfSupply(),
                false,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                order.getNotes(),
                order.getTermsAndConditions(),
                null,
                requestItems);
        InvoiceDetail draft = invoiceService.createDraft(request);
        invoiceService.applyAllocationSnapshots(
                draft.id(),
                challan != null ? challan.getItems() : null,
                order.getItems());
        invoiceService.confirmConverted(draft.id());
        return invoices.findDetailedByIdAndOrganizationId(draft.id(), orgId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found"));
    }

    private SalesOrderItem resolveOrderLineForInvoice(
            SalesOrder order, DeliveryChallan challan, Object sourceLine) {
        if (sourceLine instanceof DeliveryChallanItem dcLine) {
            if (dcLine.getSalesOrderItemId() != null) {
                return order.getItems().stream()
                        .filter(item -> item.getId().equals(dcLine.getSalesOrderItemId()))
                        .findFirst()
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.CONFLICT, "Sales order line missing for challan item"));
            }
            return order.getItems().stream()
                    .filter(item -> item.getProductId().equals(dcLine.getProductId()))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.CONFLICT, "Sales order line missing for challan item"));
        }
        return (SalesOrderItem) sourceLine;
    }

    private void applyQuotation(Quotation quotation, QuotationRequest request, Organization org) {
        Customer customer = requireCustomer(request.customerId());
        quotation.setCustomerId(request.customerId());
        quotation.setQuotationDate(request.quotationDate() == null ? LocalDate.now() : request.quotationDate());
        quotation.setExpiryDate(request.expiryDate());
        quotation.setBillingAddress(firstNonBlank(request.billingAddress(), customer.getBillingAddress()));
        quotation.setShippingAddress(
                firstNonBlank(request.shippingAddress(), customer.getShippingAddress(), customer.getBillingAddress()));
        quotation.setPlaceOfSupply(firstNonBlank(request.placeOfSupply(), customer.getStateCode()));
        quotation.setNotes(request.notes());
        quotation.setTermsAndConditions(request.termsAndConditions());
        quotation.getItems().clear();
        LineTotals totals =
                buildPricedItems(request.items(), quotation.getPlaceOfSupply(), org, (item, line, order) -> {
                    QuotationItem quotationItem = new QuotationItem();
                    quotationItem.setQuotation(quotation);
                    quotationItem.setProductId(item.productId());
                    quotationItem.setDescription(resolveLineDescription(item.productId(), item.description()));
                    quotationItem.setHsnSacCode(item.hsnSacCode());
                    quotationItem.setQuantity(item.quantity());
                    quotationItem.setUnitId(item.unitId());
                    quotationItem.setRate(item.rate());
                    quotationItem.setDiscountPercent(z(item.discountPercent()));
                    quotationItem.setDiscountAmount(line.discount());
                    quotationItem.setTaxRate(z(item.taxRate()));
                    String taxType = TaxSplitDefaults.normalizeTaxType(item.taxType());
                    String strategy = TaxSplitDefaults.normalizeStrategy(item.splitStrategy(), taxType);
                    quotationItem.setTaxType(taxType);
                    quotationItem.setSplitStrategy(strategy);
                    quotationItem.setCgstSharePercent(
                            TaxSplitDefaults.cgstShare(strategy, taxType, item.cgstSharePercent()));
                    quotationItem.setSgstSharePercent(
                            TaxSplitDefaults.sgstShare(strategy, taxType, item.sgstSharePercent()));
                    quotationItem.setTaxableAmount(line.taxable());
                    quotationItem.setCgstAmount(line.cgst());
                    quotationItem.setSgstAmount(line.sgst());
                    quotationItem.setIgstAmount(line.igst());
                    quotationItem.setLineTotal(line.lineTotal());
                    quotationItem.setLineOrder(order);
                    quotation.getItems().add(quotationItem);
                });
        quotation.setSubtotal(totals.subtotal());
        quotation.setDiscountTotal(totals.discountTotal());
        quotation.setTaxTotal(totals.taxTotal());
        quotation.setGrandTotal(totals.grandTotal());
    }

    private void applyOrder(SalesOrder order, OrderRequest request, Organization org) {
        Customer customer = requireCustomer(request.customerId());
        order.setCustomerId(request.customerId());
        order.setOrderDate(request.orderDate() == null ? LocalDate.now() : request.orderDate());
        order.setExpectedDeliveryDate(request.expectedDeliveryDate());
        order.setQuotationId(request.quotationId());
        order.setBillingAddress(firstNonBlank(request.billingAddress(), customer.getBillingAddress()));
        order.setShippingAddress(
                firstNonBlank(request.shippingAddress(), customer.getShippingAddress(), customer.getBillingAddress()));
        order.setPlaceOfSupply(firstNonBlank(request.placeOfSupply(), customer.getStateCode()));
        order.setNotes(request.notes());
        order.setTermsAndConditions(request.termsAndConditions());
        order.getItems().clear();
        LineTotals totals = buildPricedItems(request.items(), order.getPlaceOfSupply(), org, (item, line, n) -> {
            SalesOrderItem orderItem = new SalesOrderItem();
            orderItem.setSalesOrder(order);
            orderItem.setProductId(item.productId());
            orderItem.setDescription(resolveLineDescription(item.productId(), item.description()));
            orderItem.setHsnSacCode(item.hsnSacCode());
            orderItem.setQuantity(item.quantity());
            orderItem.setUnitId(item.unitId());
            orderItem.setRate(item.rate());
            orderItem.setDiscountPercent(z(item.discountPercent()));
            orderItem.setDiscountAmount(line.discount());
            orderItem.setTaxRate(z(item.taxRate()));
            String taxType = TaxSplitDefaults.normalizeTaxType(item.taxType());
            String strategy = TaxSplitDefaults.normalizeStrategy(item.splitStrategy(), taxType);
            orderItem.setTaxType(taxType);
            orderItem.setSplitStrategy(strategy);
            orderItem.setCgstSharePercent(TaxSplitDefaults.cgstShare(strategy, taxType, item.cgstSharePercent()));
            orderItem.setSgstSharePercent(TaxSplitDefaults.sgstShare(strategy, taxType, item.sgstSharePercent()));
            orderItem.setTaxableAmount(line.taxable());
            orderItem.setCgstAmount(line.cgst());
            orderItem.setSgstAmount(line.sgst());
            orderItem.setIgstAmount(line.igst());
            orderItem.setLineTotal(line.lineTotal());
            orderItem.setLineOrder(n);
            order.getItems().add(orderItem);
        });
        order.setSubtotal(totals.subtotal());
        order.setDiscountTotal(totals.discountTotal());
        order.setTaxTotal(totals.taxTotal());
        order.setGrandTotal(totals.grandTotal());
    }

    private void applyChallan(DeliveryChallan challan, ChallanRequest request) {
        challan.setCustomerId(request.customerId());
        challan.setChallanDate(request.challanDate() == null ? LocalDate.now() : request.challanDate());
        challan.setSalesOrderId(request.salesOrderId());
        challan.setWarehouseId(request.warehouseId());
        challan.setNotes(request.notes());
        challan.setTransportRequired(Boolean.TRUE.equals(request.transportRequired()));
        challan.getItems().clear();
        int i = 0;
        for (ChallanItem challanItem : request.items()) {
            DeliveryChallanItem item = new DeliveryChallanItem();
            item.setDeliveryChallan(challan);
            item.setProductId(challanItem.productId());
            item.setDescription(resolveLineDescription(challanItem.productId(), challanItem.description()));
            item.setQuantity(challanItem.quantity());
            item.setUnitId(challanItem.unitId());
            item.setLineOrder(i++);
            challan.getItems().add(item);
        }
    }

    private LineTotals buildPricedItems(
            List<Item> items, String placeOfSupply, Organization org, LineConsumer consumer) {
        BigDecimal sub = BigDecimal.ZERO;
        BigDecimal disc = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal grand = BigDecimal.ZERO;
        String pos = placeOfSupply == null || placeOfSupply.isBlank()
                ? (org.getStateCode() == null || org.getStateCode().isBlank()
                        ? "00"
                        : org.getStateCode().trim())
                : placeOfSupply.trim();
        String state = org.getStateCode() == null || org.getStateCode().isBlank()
                ? "00"
                : org.getStateCode().trim();
        String supply = pos.isBlank() ? state : pos;
        int n = 0;
        for (Item item : items) {
            BigDecimal discount = item.quantity()
                    .multiply(item.rate())
                    .multiply(z(item.discountPercent()))
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            var gstResult = gst.calculate(new GstCalculationDtos.Request(
                    state.isBlank() ? "00" : state,
                    supply.isBlank() ? "00" : supply,
                    z(item.taxRate()),
                    false,
                    item.quantity(),
                    item.rate(),
                    discount,
                    item.taxType(),
                    item.splitStrategy(),
                    item.cgstSharePercent(),
                    item.sgstSharePercent()));
            consumer.accept(
                    item,
                    new CalculatedLine(
                            discount,
                            gstResult.taxable(),
                            gstResult.cgst(),
                            gstResult.sgst(),
                            gstResult.igst().add(gstResult.otherTax()),
                            gstResult.lineTotal()),
                    n++);
            sub = sub.add(item.quantity().multiply(item.rate()));
            disc = disc.add(discount);
            tax = tax.add(gstResult.cgst())
                    .add(gstResult.sgst())
                    .add(gstResult.igst())
                    .add(gstResult.otherTax());
            grand = grand.add(gstResult.lineTotal());
        }
        return new LineTotals(sub, disc, tax, grand);
    }

    private UUID orgId() {
        return TenantContext.getOrganizationId();
    }

    private String resolveLineDescription(UUID productId, String description) {
        if (description != null && !description.isBlank()) {
            return description.trim();
        }
        if (productId == null) {
            return description;
        }
        return products.findByIdAndOrganizationId(productId, orgId())
                .map(Product::getName)
                .orElse(description);
    }

    private Customer requireCustomer(UUID customerId) {
        return customers
                .findByIdAndOrganizationId(customerId, orgId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));
    }

    private void fillPartyDefaults(SalesOrder order) {
        if (!needsPartyDefaults(order.getBillingAddress(), order.getShippingAddress(), order.getPlaceOfSupply())) {
            return;
        }
        customers.findByIdAndOrganizationId(order.getCustomerId(), orgId()).ifPresent(customer -> {
            if (isBlank(order.getBillingAddress())) {
                order.setBillingAddress(customer.getBillingAddress());
            }
            if (isBlank(order.getShippingAddress())) {
                order.setShippingAddress(firstNonBlank(customer.getShippingAddress(), customer.getBillingAddress()));
            }
            if (isBlank(order.getPlaceOfSupply())) {
                order.setPlaceOfSupply(customer.getStateCode());
            }
        });
    }

    private void fillPartyDefaults(Quotation quotation) {
        if (!needsPartyDefaults(
                quotation.getBillingAddress(), quotation.getShippingAddress(), quotation.getPlaceOfSupply())) {
            return;
        }
        customers.findByIdAndOrganizationId(quotation.getCustomerId(), orgId()).ifPresent(customer -> {
            if (isBlank(quotation.getBillingAddress())) {
                quotation.setBillingAddress(customer.getBillingAddress());
            }
            if (isBlank(quotation.getShippingAddress())) {
                quotation.setShippingAddress(
                        firstNonBlank(customer.getShippingAddress(), customer.getBillingAddress()));
            }
            if (isBlank(quotation.getPlaceOfSupply())) {
                quotation.setPlaceOfSupply(customer.getStateCode());
            }
        });
    }

    private static boolean needsPartyDefaults(String billing, String shipping, String placeOfSupply) {
        return isBlank(billing) || isBlank(shipping) || isBlank(placeOfSupply);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private Organization organization() {
        return organizations
                .findById(orgId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));
    }

    private static BigDecimal z(BigDecimal x) {
        return x == null ? BigDecimal.ZERO : x;
    }

    @FunctionalInterface
    private interface LineConsumer {
        void accept(Item item, CalculatedLine line, int order);
    }

    private record CalculatedLine(
            BigDecimal discount,
            BigDecimal taxable,
            BigDecimal cgst,
            BigDecimal sgst,
            BigDecimal igst,
            BigDecimal lineTotal) {}

    private record LineTotals(
            BigDecimal subtotal, BigDecimal discountTotal, BigDecimal taxTotal, BigDecimal grandTotal) {}
}
