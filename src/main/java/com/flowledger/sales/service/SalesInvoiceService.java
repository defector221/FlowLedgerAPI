package com.flowledger.sales.service;

import com.flowledger.common.tenant.TenantContext;
import com.flowledger.common.util.DocumentNumberService;
import com.flowledger.inventory.dto.InventoryDtos.PostTransaction;
import com.flowledger.inventory.entity.InventoryTransaction.Type;
import com.flowledger.inventory.service.InventoryService;
import com.flowledger.organization.entity.Organization;
import com.flowledger.organization.repository.OrganizationRepository;
import com.flowledger.sales.dto.SalesDtos.*;
import com.flowledger.sales.entity.*;
import com.flowledger.sales.repository.SalesInvoiceRepository;
import com.flowledger.search.event.SearchIndexEventPublisher;
import com.flowledger.search.model.SearchEntityType;
import com.flowledger.tax.dto.GstCalculationDtos;
import com.flowledger.tax.service.GstCalculationService;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SalesInvoiceService {
    private final SalesInvoiceRepository repo;
    private final InventoryService inventory;
    private final OrganizationRepository orgs;
    private final DocumentNumberService numbers;
    private final GstCalculationService gst;
    private final SearchIndexEventPublisher searchEvents;

    public SalesInvoiceService(
            SalesInvoiceRepository r,
            InventoryService i,
            OrganizationRepository o,
            DocumentNumberService n,
            GstCalculationService g,
            SearchIndexEventPublisher searchEvents) {
        repo = r;
        inventory = i;
        orgs = o;
        numbers = n;
        gst = g;
        this.searchEvents = searchEvents;
    }

    @Transactional
    public SalesInvoice createDraft(Invoice d) {
        SalesInvoice i = new SalesInvoice();
        i.setOrganizationId(TenantContext.getOrganizationId());
        apply(i, d);
        SalesInvoice saved = repo.save(i);
        searchEvents.upsert(saved.getOrganizationId(), SearchEntityType.SALES_INVOICE, saved.getId());
        return saved;
    }

    @Transactional
    public SalesInvoice updateDraft(UUID id, Invoice d) {
        SalesInvoice i = get(id);
        if (i.getStatus() != SalesInvoice.Status.DRAFT)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only draft invoices can be updated");
        apply(i, d);
        SalesInvoice saved = repo.save(i);
        searchEvents.upsert(saved.getOrganizationId(), SearchEntityType.SALES_INVOICE, saved.getId());
        return saved;
    }

    @Transactional
    public SalesInvoice confirm(UUID id) {
        SalesInvoice i = get(id);
        if (i.getStatus() == SalesInvoice.Status.CANCELLED)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cancelled invoice cannot be confirmed");
        if (i.getStatus() == SalesInvoice.Status.CONFIRMED && i.isInventoryPosted()) return i;
        Organization o = orgs.findById(i.getOrganizationId()).orElseThrow();
        if (i.getInvoiceNumber() == null || i.getInvoiceNumber().isBlank())
            i.setInvoiceNumber(numbers.next(
                    o.getId(),
                    "SALES_INVOICE",
                    o.getInvoicePrefix(),
                    o.getInvoiceNumberFormat(),
                    o.getFinancialYearStart(),
                    i.getInvoiceDate()));
        recalculate(i, o);
        if (!i.isInventoryPosted()) {
            if (i.getWarehouseId() == null)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Warehouse is required to confirm invoice");
            for (var line : i.getItems()) {
                BigDecimal available = inventory
                        .getStock(line.getProductId(), i.getWarehouseId())
                        .available();
                if (!o.isAllowNegativeStock() && available.compareTo(line.getQuantity()) < 0)
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT, "Insufficient stock for product " + line.getProductId());
                inventory.postTransaction(new PostTransaction(
                        Type.SALE,
                        line.getProductId(),
                        i.getWarehouseId(),
                        BigDecimal.ZERO,
                        line.getQuantity(),
                        "SALES_INVOICE",
                        i.getId(),
                        i.getInvoiceNumber(),
                        "invoice:" + i.getId() + ":" + line.getId(),
                        null,
                        null,
                        null,
                        null,
                        i.getNotes(),
                        i.getInvoiceDate()));
            }
            i.setInventoryPosted(true);
        }
        i.setStatus(SalesInvoice.Status.CONFIRMED);
        SalesInvoice saved = repo.save(i);
        searchEvents.upsert(saved.getOrganizationId(), SearchEntityType.SALES_INVOICE, saved.getId());
        return saved;
    }

    @Transactional
    public SalesInvoice cancel(UUID id) {
        SalesInvoice i = get(id);
        if (i.getStatus() == SalesInvoice.Status.CANCELLED) return i;
        if (i.isInventoryPosted())
            for (var line : i.getItems())
                inventory.postTransaction(new PostTransaction(
                        Type.SALES_RETURN,
                        line.getProductId(),
                        i.getWarehouseId(),
                        line.getQuantity(),
                        BigDecimal.ZERO,
                        "SALES_INVOICE_CANCEL",
                        i.getId(),
                        i.getInvoiceNumber(),
                        "invoice-cancel:" + i.getId() + ":" + line.getId(),
                        null,
                        null,
                        null,
                        null,
                        "Invoice cancellation",
                        LocalDate.now()));
        i.setInventoryPosted(false);
        i.setStatus(SalesInvoice.Status.CANCELLED);
        SalesInvoice saved = repo.save(i);
        searchEvents.upsert(saved.getOrganizationId(), SearchEntityType.SALES_INVOICE, saved.getId());
        return saved;
    }

    @Transactional(readOnly = true)
    public SalesInvoice get(UUID id) {
        return repo.findByIdAndOrganizationId(id, TenantContext.getOrganizationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found"));
    }

    @Transactional(readOnly = true)
    public List<SalesInvoice> list(String status, UUID customerId) {
        return repo.findByOrganizationIdOrderByInvoiceDateDesc(TenantContext.getOrganizationId()).stream()
                .filter(i -> status == null || i.getStatus().name().equals(status))
                .filter(i -> customerId == null || customerId.equals(i.getCustomerId()))
                .toList();
    }

    private void apply(SalesInvoice i, Invoice d) {
        i.setCustomerId(d.customerId());
        i.setInvoiceDate(d.invoiceDate() == null ? LocalDate.now() : d.invoiceDate());
        i.setDueDate(d.dueDate());
        i.setWarehouseId(d.warehouseId());
        i.setSalesOrderId(d.salesOrderId());
        i.setDeliveryChallanId(d.deliveryChallanId());
        i.setBillingAddress(d.billingAddress());
        i.setShippingAddress(d.shippingAddress());
        i.setPlaceOfSupply(d.placeOfSupply());
        i.setTaxInclusive(Boolean.TRUE.equals(d.taxInclusive()));
        i.setShippingCharges(z(d.shippingCharges()));
        i.setAdditionalCharges(z(d.additionalCharges()));
        i.setRoundOff(z(d.roundOff()));
        i.setNotes(d.notes());
        i.setTermsAndConditions(d.termsAndConditions());
        i.getItems().clear();
        int n = 0;
        for (Item dline : d.items()) {
            SalesInvoiceItem l = new SalesInvoiceItem();
            l.setSalesInvoice(i);
            l.setProductId(dline.productId());
            l.setDescription(dline.description());
            l.setHsnSacCode(dline.hsnSacCode());
            l.setQuantity(dline.quantity());
            l.setUnitId(dline.unitId());
            l.setRate(dline.rate());
            l.setDiscountPercent(z(dline.discountPercent()));
            l.setTaxRate(z(dline.taxRate()));
            l.setTaxType(dline.taxType() == null || dline.taxType().isBlank() ? "GST" : dline.taxType().trim().toUpperCase());
            l.setLineOrder(n++);
            i.getItems().add(l);
        }
    }

    private void recalculate(SalesInvoice i, Organization o) {
        BigDecimal sub = BigDecimal.ZERO,
                disc = BigDecimal.ZERO,
                taxable = BigDecimal.ZERO,
                cgst = BigDecimal.ZERO,
                sgst = BigDecimal.ZERO,
                igst = BigDecimal.ZERO;
        String pos = i.getPlaceOfSupply() == null ? o.getStateCode() : i.getPlaceOfSupply();
        for (var l : i.getItems()) {
            BigDecimal discount = l.getQuantity()
                    .multiply(l.getRate())
                    .multiply(l.getDiscountPercent())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            var r = gst.calculate(new GstCalculationDtos.Request(
                    o.getStateCode(),
                    pos,
                    l.getTaxRate(),
                    i.isTaxInclusive(),
                    l.getQuantity(),
                    l.getRate(),
                    discount,
                    l.getTaxType()));
            l.setDiscountAmount(discount);
            l.setTaxableAmount(r.taxable());
            l.setCgstAmount(r.cgst());
            l.setSgstAmount(r.sgst());
            l.setIgstAmount(r.igst().add(r.otherTax()));
            applyComponentRates(l, r);
            l.setLineTotal(r.lineTotal());
            sub = sub.add(l.getQuantity().multiply(l.getRate()));
            disc = disc.add(discount);
            taxable = taxable.add(r.taxable());
            cgst = cgst.add(r.cgst());
            sgst = sgst.add(r.sgst());
            igst = igst.add(r.igst()).add(r.otherTax());
        }
        i.setSubtotal(sub);
        i.setDiscountTotal(disc);
        i.setTaxableAmount(taxable);
        i.setCgstTotal(cgst);
        i.setSgstTotal(sgst);
        i.setIgstTotal(igst);
        i.setGrandTotal(i.getItems().stream()
                .map(SalesInvoiceItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .add(i.getShippingCharges())
                .add(i.getAdditionalCharges())
                .add(i.getRoundOff()));
        i.setOutstandingAmount(i.getGrandTotal().subtract(i.getAmountPaid()));
    }

    private static void applyComponentRates(SalesInvoiceItem line, GstCalculationDtos.Response r) {
        BigDecimal rate = line.getTaxRate();
        if (r.cgst().signum() > 0) {
            line.setCgstRate(rate.divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP));
            line.setSgstRate(line.getCgstRate());
            line.setIgstRate(BigDecimal.ZERO);
        } else if (r.igst().signum() > 0 || r.otherTax().signum() > 0) {
            line.setCgstRate(BigDecimal.ZERO);
            line.setSgstRate(BigDecimal.ZERO);
            line.setIgstRate(rate);
        } else {
            line.setCgstRate(BigDecimal.ZERO);
            line.setSgstRate(BigDecimal.ZERO);
            line.setIgstRate(BigDecimal.ZERO);
        }
    }

    private static BigDecimal z(BigDecimal x) {
        return x == null ? BigDecimal.ZERO : x;
    }
}
