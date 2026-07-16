package com.flowledger.sales.service;

import com.flowledger.accounting.service.AccountingPostingService;
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
            AccountingPostingService accounting) {
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
    }

    // ── Quotations ──────────────────────────────────────────────────────────

    @Transactional
    public Quotation createQuotation(QuotationRequest request) {
        Organization org = organization();
        Quotation q = new Quotation();
        q.setOrganizationId(orgId());
        q.setStatus(Quotation.Status.DRAFT);
        applyQuotation(q, request, org);
        q.setQuotationNumber(numbers.next(
                org.getId(),
                "QUOTATION",
                org.getQuotationPrefix(),
                NUMBER_FORMAT,
                org.getFinancialYearStart(),
                q.getQuotationDate()));
        return quotations.save(q);
    }

    @Transactional
    public Quotation updateQuotation(UUID id, QuotationRequest request) {
        Quotation q = getQuotation(id);
        if (q.getStatus() != Quotation.Status.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only draft quotations can be updated");
        }
        applyQuotation(q, request, organization());
        return quotations.save(q);
    }

    @Transactional(readOnly = true)
    public List<Quotation> listQuotations() {
        return quotations.findByOrganizationIdOrderByQuotationDateDesc(orgId());
    }

    @Transactional(readOnly = true)
    public Quotation getQuotation(UUID id) {
        return quotations
                .findByIdAndOrganizationId(id, orgId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Quotation not found"));
    }

    @Transactional
    public Quotation cancelQuotation(UUID id) {
        Quotation q = getQuotation(id);
        if (q.getStatus() == Quotation.Status.CANCELLED) {
            return q;
        }
        if (q.getStatus() == Quotation.Status.CONVERTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Converted quotation cannot be cancelled");
        }
        q.setStatus(Quotation.Status.CANCELLED);
        return quotations.save(q);
    }

    @Transactional
    public SalesOrder convertQuotationToOrder(UUID quotationId) {
        Quotation q = getQuotation(quotationId);
        if (q.getConvertedToOrderId() != null) {
            return orders.findByIdAndOrganizationId(q.getConvertedToOrderId(), orgId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Converted order missing"));
        }
        if (q.getStatus() == Quotation.Status.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cancelled quotation cannot be converted");
        }
        Organization org = organization();
        SalesOrder order = new SalesOrder();
        order.setOrganizationId(orgId());
        order.setCustomerId(q.getCustomerId());
        order.setOrderDate(LocalDate.now());
        order.setQuotationId(q.getId());
        order.setBillingAddress(q.getBillingAddress());
        order.setShippingAddress(q.getShippingAddress());
        order.setPlaceOfSupply(q.getPlaceOfSupply());
        order.setTermsAndConditions(q.getTermsAndConditions());
        order.setNotes(q.getNotes());
        order.setOrderNumber(numbers.next(
                org.getId(),
                "SALES_ORDER",
                org.getSalesOrderPrefix(),
                NUMBER_FORMAT,
                org.getFinancialYearStart(),
                order.getOrderDate()));
        order.setSubtotal(q.getSubtotal());
        order.setDiscountTotal(q.getDiscountTotal());
        order.setTaxTotal(q.getTaxTotal());
        order.setGrandTotal(q.getGrandTotal());
        order.setStatus(SalesOrder.Status.CONFIRMED);
        int i = 0;
        for (QuotationItem qi : q.getItems()) {
            SalesOrderItem item = new SalesOrderItem();
            item.setSalesOrder(order);
            item.setProductId(qi.getProductId());
            item.setDescription(qi.getDescription());
            item.setHsnSacCode(qi.getHsnSacCode());
            item.setQuantity(qi.getQuantity());
            item.setUnitId(qi.getUnitId());
            item.setRate(qi.getRate());
            item.setDiscountPercent(qi.getDiscountPercent());
            item.setDiscountAmount(qi.getDiscountAmount());
            item.setTaxRate(qi.getTaxRate());
            item.setTaxType(qi.getTaxType() == null || qi.getTaxType().isBlank() ? "GST" : qi.getTaxType());
            item.setSplitStrategy(
                    qi.getSplitStrategy() == null || qi.getSplitStrategy().isBlank()
                            ? "PLACE_OF_SUPPLY"
                            : qi.getSplitStrategy());
            item.setCgstSharePercent(
                    qi.getCgstSharePercent() == null ? new BigDecimal("50") : qi.getCgstSharePercent());
            item.setSgstSharePercent(
                    qi.getSgstSharePercent() == null ? new BigDecimal("50") : qi.getSgstSharePercent());
            item.setTaxableAmount(qi.getTaxableAmount());
            item.setCgstAmount(qi.getCgstAmount());
            item.setSgstAmount(qi.getSgstAmount());
            item.setIgstAmount(qi.getIgstAmount());
            item.setLineTotal(qi.getLineTotal());
            item.setLineOrder(i++);
            order.getItems().add(item);
        }
        SalesOrder saved = orders.save(order);
        q.setConvertedToOrderId(saved.getId());
        q.setStatus(Quotation.Status.CONVERTED);
        quotations.save(q);
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
        return orders.findByIdAndOrganizationId(id, orgId())
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
        for (SalesOrderItem oi : order.getItems()) {
            DeliveryChallanItem item = new DeliveryChallanItem();
            item.setDeliveryChallan(challan);
            item.setProductId(oi.getProductId());
            item.setDescription(oi.getDescription());
            item.setQuantity(oi.getQuantity());
            item.setUnitId(oi.getUnitId());
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
        SalesReturn sr = new SalesReturn();
        sr.setOrganizationId(orgId());
        sr.setSalesInvoiceId(invoice.getId());
        sr.setCustomerId(invoice.getCustomerId());
        sr.setReturnDate(request.returnDate() == null ? LocalDate.now() : request.returnDate());
        sr.setStatus("DRAFT");
        sr.setNotes(request.notes());
        sr.setReturnNumber(numbers.next(
                org.getId(), "SALES_RETURN", "SR", NUMBER_FORMAT, org.getFinancialYearStart(), sr.getReturnDate()));
        BigDecimal total = BigDecimal.ZERO;
        int i = 0;
        for (ReturnItem ri : request.items()) {
            SalesReturnItem item = new SalesReturnItem();
            item.setSalesReturn(sr);
            item.setProductId(ri.productId());
            item.setQuantity(ri.quantity());
            item.setRate(ri.rate());
            item.setLineTotal(ri.quantity().multiply(ri.rate()).setScale(2, RoundingMode.HALF_UP));
            item.setLineOrder(i++);
            total = total.add(item.getLineTotal());
            sr.getItems().add(item);
        }
        sr.setGrandTotal(total);
        return returns.save(sr);
    }

    @Transactional
    public SalesReturn confirmReturn(UUID id) {
        SalesReturn sr = getReturn(id);
        if ("CANCELLED".equals(sr.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cancelled return cannot be confirmed");
        }
        if ("CONFIRMED".equals(sr.getStatus()) && sr.isInventoryPosted()) {
            return sr;
        }
        SalesInvoice invoice = invoices.findByIdAndOrganizationId(sr.getSalesInvoiceId(), orgId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found"));
        if (invoice.getWarehouseId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invoice has no warehouse for stock return");
        }
        if (!sr.isInventoryPosted()) {
            for (SalesReturnItem line : sr.getItems()) {
                inventory.postTransaction(new PostTransaction(
                        Type.SALES_RETURN,
                        line.getProductId(),
                        invoice.getWarehouseId(),
                        line.getQuantity(),
                        BigDecimal.ZERO,
                        "SALES_RETURN",
                        sr.getId(),
                        sr.getReturnNumber(),
                        "sales-return:" + sr.getId() + ":" + line.getId(),
                        null,
                        null,
                        null,
                        null,
                        sr.getNotes(),
                        sr.getReturnDate()));
            }
            sr.setInventoryPosted(true);
        }
        sr.setStatus("CONFIRMED");
        SalesReturn saved = returns.save(sr);
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
        CreditNote cn = new CreditNote();
        cn.setOrganizationId(orgId());
        cn.setCustomerId(request.customerId());
        cn.setSalesReturnId(request.salesReturnId());
        cn.setSalesInvoiceId(request.salesInvoiceId());
        cn.setCreditNoteDate(request.creditNoteDate() == null ? LocalDate.now() : request.creditNoteDate());
        cn.setAmount(request.amount());
        cn.setNotes(request.notes());
        cn.setStatus("ISSUED");
        cn.setCreditNoteNumber(numbers.next(
                org.getId(), "CREDIT_NOTE", "CN", NUMBER_FORMAT, org.getFinancialYearStart(), cn.getCreditNoteDate()));
        CreditNote saved = creditNotes.save(cn);
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
                .map(i -> new Item(
                        i.getProductId(),
                        i.getDescription(),
                        i.getHsnSacCode(),
                        i.getQuantity(),
                        i.getUnitId(),
                        i.getRate(),
                        i.getDiscountPercent(),
                        i.getTaxRate(),
                        i.getTaxType(),
                        i.getSplitStrategy(),
                        i.getCgstSharePercent(),
                        i.getSgstSharePercent()))
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

    private void applyQuotation(Quotation q, QuotationRequest request, Organization org) {
        q.setCustomerId(request.customerId());
        q.setQuotationDate(request.quotationDate() == null ? LocalDate.now() : request.quotationDate());
        q.setExpiryDate(request.expiryDate());
        q.setBillingAddress(request.billingAddress());
        q.setShippingAddress(request.shippingAddress());
        q.setPlaceOfSupply(request.placeOfSupply());
        q.setNotes(request.notes());
        q.setTermsAndConditions(request.termsAndConditions());
        q.getItems().clear();
        LineTotals totals = buildPricedItems(request.items(), request.placeOfSupply(), org, (item, line, order) -> {
            QuotationItem qi = new QuotationItem();
            qi.setQuotation(q);
            qi.setProductId(item.productId());
            qi.setDescription(item.description());
            qi.setHsnSacCode(item.hsnSacCode());
            qi.setQuantity(item.quantity());
            qi.setUnitId(item.unitId());
            qi.setRate(item.rate());
            qi.setDiscountPercent(z(item.discountPercent()));
            qi.setDiscountAmount(line.discount());
            qi.setTaxRate(z(item.taxRate()));
            String taxType = TaxSplitDefaults.normalizeTaxType(item.taxType());
            String strategy = TaxSplitDefaults.normalizeStrategy(item.splitStrategy(), taxType);
            qi.setTaxType(taxType);
            qi.setSplitStrategy(strategy);
            qi.setCgstSharePercent(TaxSplitDefaults.cgstShare(strategy, taxType, item.cgstSharePercent()));
            qi.setSgstSharePercent(TaxSplitDefaults.sgstShare(strategy, taxType, item.sgstSharePercent()));
            qi.setTaxableAmount(line.taxable());
            qi.setCgstAmount(line.cgst());
            qi.setSgstAmount(line.sgst());
            qi.setIgstAmount(line.igst());
            qi.setLineTotal(line.lineTotal());
            qi.setLineOrder(order);
            q.getItems().add(qi);
        });
        q.setSubtotal(totals.subtotal());
        q.setDiscountTotal(totals.discountTotal());
        q.setTaxTotal(totals.taxTotal());
        q.setGrandTotal(totals.grandTotal());
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
            SalesOrderItem oi = new SalesOrderItem();
            oi.setSalesOrder(order);
            oi.setProductId(item.productId());
            oi.setDescription(item.description());
            oi.setHsnSacCode(item.hsnSacCode());
            oi.setQuantity(item.quantity());
            oi.setUnitId(item.unitId());
            oi.setRate(item.rate());
            oi.setDiscountPercent(z(item.discountPercent()));
            oi.setDiscountAmount(line.discount());
            oi.setTaxRate(z(item.taxRate()));
            String taxType = TaxSplitDefaults.normalizeTaxType(item.taxType());
            String strategy = TaxSplitDefaults.normalizeStrategy(item.splitStrategy(), taxType);
            oi.setTaxType(taxType);
            oi.setSplitStrategy(strategy);
            oi.setCgstSharePercent(TaxSplitDefaults.cgstShare(strategy, taxType, item.cgstSharePercent()));
            oi.setSgstSharePercent(TaxSplitDefaults.sgstShare(strategy, taxType, item.sgstSharePercent()));
            oi.setTaxableAmount(line.taxable());
            oi.setCgstAmount(line.cgst());
            oi.setSgstAmount(line.sgst());
            oi.setIgstAmount(line.igst());
            oi.setLineTotal(line.lineTotal());
            oi.setLineOrder(n);
            order.getItems().add(oi);
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
        challan.getItems().clear();
        int i = 0;
        for (ChallanItem ci : request.items()) {
            DeliveryChallanItem item = new DeliveryChallanItem();
            item.setDeliveryChallan(challan);
            item.setProductId(ci.productId());
            item.setDescription(ci.description());
            item.setQuantity(ci.quantity());
            item.setUnitId(ci.unitId());
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
            var r = gst.calculate(new GstCalculationDtos.Request(
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
                            discount, r.taxable(), r.cgst(), r.sgst(), r.igst().add(r.otherTax()), r.lineTotal()),
                    n++);
            sub = sub.add(item.quantity().multiply(item.rate()));
            disc = disc.add(discount);
            tax = tax.add(r.cgst()).add(r.sgst()).add(r.igst()).add(r.otherTax());
            grand = grand.add(r.lineTotal());
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
