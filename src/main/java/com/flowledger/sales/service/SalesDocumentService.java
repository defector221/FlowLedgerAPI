package com.flowledger.sales.service;

import com.flowledger.accounting.service.AccountingPostingService;
import com.flowledger.ai.workflow.AiWorkflowGateService;
import com.flowledger.common.tenant.TenantContext;
import com.flowledger.common.util.DocumentNumberService;
import com.flowledger.inventory.dto.InventoryDtos.PostTransaction;
import com.flowledger.inventory.entity.InventoryTransaction.Type;
import com.flowledger.inventory.service.InventoryService;
import com.flowledger.organization.entity.Organization;
import com.flowledger.organization.repository.OrganizationRepository;
import com.flowledger.sales.dto.SalesDtos.*;
import com.flowledger.sales.entity.*;
import com.flowledger.sales.repository.*;
import com.flowledger.tax.TaxSplitDefaults;
import com.flowledger.tax.dto.GstCalculationDtos;
import com.flowledger.tax.service.GstCalculationService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
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
    private final DocumentNumberService numbers;
    private final SalesInvoiceService invoiceService;
    private final GstCalculationService gst;
    private final InventoryService inventory;
    private final AccountingPostingService accounting;
    private final ObjectProvider<AiWorkflowGateService> workflowGate;

    public SalesDocumentService(
            QuotationRepository quotations,
            SalesOrderRepository orders,
            DeliveryChallanRepository challans,
            SalesInvoiceRepository invoices,
            SalesReturnRepository returns,
            CreditNoteRepository creditNotes,
            OrganizationRepository organizations,
            DocumentNumberService numbers,
            SalesInvoiceService invoiceService,
            GstCalculationService gst,
            InventoryService inventory,
            AccountingPostingService accounting,
            ObjectProvider<AiWorkflowGateService> workflowGate) {
        this.quotations = quotations;
        this.orders = orders;
        this.challans = challans;
        this.invoices = invoices;
        this.returns = returns;
        this.creditNotes = creditNotes;
        this.organizations = organizations;
        this.numbers = numbers;
        this.invoiceService = invoiceService;
        this.gst = gst;
        this.inventory = inventory;
        this.accounting = accounting;
        this.workflowGate = workflowGate;
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
    public List<Quotation> listQuotations() {
        return quotations.findByOrganizationIdOrderByQuotationDateDesc(orgId());
    }

    @Transactional(readOnly = true)
    public Quotation getQuotation(UUID id) {
        return quotations
                .findDetailedByIdAndOrganizationId(id, orgId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Quotation not found"));
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
    public List<SalesOrder> listOrders() {
        return orders.findByOrganizationIdOrderByOrderDateDesc(orgId());
    }

    @Transactional(readOnly = true)
    public SalesOrder getOrder(UUID id) {
        return orders.findDetailedByIdAndOrganizationId(id, orgId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sales order not found"));
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
        order.setStatus(SalesOrder.Status.CANCELLED);
        return orders.save(order);
    }

    @Transactional
    public DeliveryChallan convertOrderToChallan(UUID orderId, UUID warehouseId) {
        SalesOrder order = getOrder(orderId);
        if (order.getStatus() == SalesOrder.Status.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cancelled order cannot be converted");
        }
        gate("SALES_ORDER", order.getId(), order.getGrandTotal(), "convert to delivery challan");
        challans.findByOrganizationIdAndSalesOrderId(orgId(), orderId).ifPresent(existing -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Challan already exists for order");
        });
        Organization org = organization();
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
            DeliveryChallanItem item = new DeliveryChallanItem();
            item.setDeliveryChallan(challan);
            item.setProductId(orderItem.getProductId());
            item.setDescription(orderItem.getDescription());
            item.setQuantity(orderItem.getQuantity());
            item.setUnitId(orderItem.getUnitId());
            item.setLineOrder(i++);
            challan.getItems().add(item);
        }
        return challans.save(challan);
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

    @Transactional(readOnly = true)
    public List<DeliveryChallan> listChallans() {
        return challans.findByOrganizationIdOrderByChallanDateDesc(orgId());
    }

    @Transactional(readOnly = true)
    public DeliveryChallan getChallan(UUID id) {
        return challans.findByIdAndOrganizationId(id, orgId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Challan not found"));
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

    @Transactional
    public SalesInvoice convertChallanToInvoice(UUID challanId) {
        DeliveryChallan challan = getChallan(challanId);
        if (challan.getStatus() == DeliveryChallan.Status.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cancelled challan cannot be converted");
        }
        return invoices.findByOrganizationIdAndDeliveryChallanId(orgId(), challanId)
                .orElseGet(() -> {
                    SalesOrder order = challan.getSalesOrderId() == null
                            ? null
                            : orders.findByIdAndOrganizationId(challan.getSalesOrderId(), orgId())
                                    .orElse(null);
                    if (order == null) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Challan has no linked sales order");
                    }
                    gate("SALES_ORDER", order.getId(), order.getGrandTotal(), "convert challan to invoice");
                    return createInvoiceFromOrder(order, challan.getWarehouseId(), challan.getId());
                });
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
        accounting.postSalesReturn(saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public SalesReturn getReturn(UUID id) {
        return returns.findByIdAndOrganizationId(id, orgId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sales return not found"));
    }

    @Transactional(readOnly = true)
    public List<SalesReturn> listReturns() {
        return returns.findByOrganizationIdOrderByReturnDateDesc(orgId());
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
        accounting.postCreditNote(saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<CreditNote> listCreditNotes() {
        return creditNotes.findByOrganizationIdOrderByCreditNoteDateDesc(orgId());
    }

    @Transactional(readOnly = true)
    public CreditNote getCreditNote(UUID id) {
        return creditNotes
                .findByIdAndOrganizationId(id, orgId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Credit note not found"));
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private SalesInvoice createInvoiceFromOrder(SalesOrder order, UUID warehouseId, UUID challanId) {
        var requestItems = order.getItems().stream()
                .map(orderLine -> new Item(
                        orderLine.getProductId(),
                        orderLine.getDescription(),
                        orderLine.getHsnSacCode(),
                        orderLine.getQuantity(),
                        orderLine.getUnitId(),
                        orderLine.getRate(),
                        orderLine.getDiscountPercent(),
                        orderLine.getTaxRate(),
                        orderLine.getTaxType(),
                        orderLine.getSplitStrategy(),
                        orderLine.getCgstSharePercent(),
                        orderLine.getSgstSharePercent()))
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
        invoiceService.confirm(draft.id());
        return invoices.findDetailedByIdAndOrganizationId(draft.id(), orgId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found"));
    }

    private void applyQuotation(Quotation quotation, QuotationRequest request, Organization org) {
        quotation.setCustomerId(request.customerId());
        quotation.setQuotationDate(request.quotationDate() == null ? LocalDate.now() : request.quotationDate());
        quotation.setExpiryDate(request.expiryDate());
        quotation.setBillingAddress(request.billingAddress());
        quotation.setShippingAddress(request.shippingAddress());
        quotation.setPlaceOfSupply(request.placeOfSupply());
        quotation.setNotes(request.notes());
        quotation.setTermsAndConditions(request.termsAndConditions());
        quotation.getItems().clear();
        LineTotals totals = buildPricedItems(request.items(), request.placeOfSupply(), org, (item, line, order) -> {
            QuotationItem quotationItem = new QuotationItem();
            quotationItem.setQuotation(quotation);
            quotationItem.setProductId(item.productId());
            quotationItem.setDescription(item.description());
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
            quotationItem.setCgstSharePercent(TaxSplitDefaults.cgstShare(strategy, taxType, item.cgstSharePercent()));
            quotationItem.setSgstSharePercent(TaxSplitDefaults.sgstShare(strategy, taxType, item.sgstSharePercent()));
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
        order.setCustomerId(request.customerId());
        order.setOrderDate(request.orderDate() == null ? LocalDate.now() : request.orderDate());
        order.setExpectedDeliveryDate(request.expectedDeliveryDate());
        order.setQuotationId(request.quotationId());
        order.setBillingAddress(request.billingAddress());
        order.setShippingAddress(request.shippingAddress());
        order.setPlaceOfSupply(request.placeOfSupply());
        order.setNotes(request.notes());
        order.setTermsAndConditions(request.termsAndConditions());
        order.getItems().clear();
        LineTotals totals = buildPricedItems(request.items(), request.placeOfSupply(), org, (item, line, n) -> {
            SalesOrderItem orderItem = new SalesOrderItem();
            orderItem.setSalesOrder(order);
            orderItem.setProductId(item.productId());
            orderItem.setDescription(item.description());
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
            item.setDescription(challanItem.description());
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
