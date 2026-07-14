package com.flowledger.purchase.service;

import com.flowledger.accounting.domain.AccountingStatus;
import com.flowledger.accounting.domain.JournalSource;
import com.flowledger.accounting.service.AccountingPostingService;
import com.flowledger.common.tenant.TenantContext;
import com.flowledger.common.util.DocumentNumberService;
import com.flowledger.organization.entity.Organization;
import com.flowledger.organization.repository.OrganizationRepository;
import com.flowledger.purchase.dto.PurchaseDtos.InvoiceRequest;
import com.flowledger.purchase.dto.PurchaseDtos.Line;
import com.flowledger.purchase.entity.GoodsReceipt;
import com.flowledger.purchase.entity.PurchaseInvoice;
import com.flowledger.purchase.entity.PurchaseInvoiceItem;
import com.flowledger.purchase.entity.PurchaseOrder;
import com.flowledger.search.event.SearchIndexEventPublisher;
import com.flowledger.search.model.SearchEntityType;
import com.flowledger.tax.TaxSplitDefaults;
import com.flowledger.tax.dto.GstCalculationDtos;
import com.flowledger.tax.service.GstCalculationService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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
@Transactional
public class PurchaseInvoiceService {
    @PersistenceContext
    private EntityManager em;

    private final GoodsReceiptService grns;
    private final PurchaseOrderService orders;
    private final DocumentNumberService numbers;
    private final OrganizationRepository organizations;
    private final GstCalculationService gst;
    private final SearchIndexEventPublisher searchEvents;
    private final AccountingPostingService accounting;

    public PurchaseInvoiceService(
            GoodsReceiptService g,
            PurchaseOrderService o,
            DocumentNumberService n,
            OrganizationRepository r,
            GstCalculationService tax,
            SearchIndexEventPublisher searchEvents,
            AccountingPostingService accounting) {
        grns = g;
        orders = o;
        numbers = n;
        organizations = r;
        gst = tax;
        this.searchEvents = searchEvents;
        this.accounting = accounting;
    }

    public PurchaseInvoice fromGrn(UUID grnId, InvoiceRequest r) {
        GoodsReceipt g = grns.get(grnId);
        PurchaseInvoice duplicate = em.createQuery(
                        "from PurchaseInvoice i where i.organizationId=:org and i.goodsReceiptId=:grn",
                        PurchaseInvoice.class)
                .setParameter("org", TenantContext.getOrganizationId())
                .setParameter("grn", grnId)
                .getResultStream()
                .findFirst()
                .orElse(null);
        if (duplicate != null) return duplicate;
        return create(
                g.getSupplierId(),
                g.getPurchaseOrderId(),
                grnId,
                g.getWarehouseId(),
                r,
                g.getItems().stream()
                        .map(x -> new Line(
                                x.getProductId(),
                                x.getUnitId(),
                                x.getDescription(),
                                null,
                                x.getQuantity(),
                                BigDecimal.ZERO,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null))
                        .toList());
    }

    public PurchaseInvoice fromPo(UUID poId, InvoiceRequest r) {
        PurchaseOrder p = orders.get(poId);
        return create(
                p.getSupplierId(),
                poId,
                null,
                null,
                r,
                r.items() == null
                        ? p.getItems().stream()
                                .map(x -> new Line(
                                        x.getProductId(),
                                        x.getUnitId(),
                                        x.getDescription(),
                                        x.getHsnSacCode(),
                                        x.getQuantity(),
                                        x.getRate(),
                                        x.getDiscountPercent(),
                                        x.getTaxRate(),
                                        x.getTaxType(),
                                        x.getSplitStrategy(),
                                        x.getCgstSharePercent(),
                                        x.getSgstSharePercent()))
                                .toList()
                        : r.items());
    }

    public PurchaseInvoice confirm(UUID id) {
        PurchaseInvoice i = get(id);
        if ("CANCELLED".equals(i.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cancelled invoice cannot be confirmed");
        if (!"DRAFT".equals(i.getStatus())) return i;
        i.setStatus(i.getOutstandingAmount().signum() == 0 ? "PAID" : "CONFIRMED");
        accounting.postPurchaseInvoice(i);
        searchEvents.upsert(i.getOrganizationId(), SearchEntityType.PURCHASE_INVOICE, i.getId());
        return i;
    }

    public PurchaseInvoice cancel(UUID id) {
        PurchaseInvoice i = get(id);
        if ("CANCELLED".equals(i.getStatus())) return i;
        if (i.getAmountPaid() != null && i.getAmountPaid().signum() > 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Paid invoice cannot be cancelled");
        i.setStatus("CANCELLED");
        if (i.getAccountingStatus() == AccountingStatus.POSTED) {
            accounting.reverseDocumentJournal(i.getOrganizationId(), JournalSource.PURCHASE_INVOICE, i.getId());
            i.setAccountingStatus(AccountingStatus.REVERSED);
        }
        searchEvents.upsert(i.getOrganizationId(), SearchEntityType.PURCHASE_INVOICE, i.getId());
        return i;
    }

    public PurchaseInvoice get(UUID id) {
        PurchaseInvoice i = em.find(PurchaseInvoice.class, id);
        if (i == null || !i.getOrganizationId().equals(TenantContext.getOrganizationId()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Purchase invoice not found");
        return i;
    }

    public List<PurchaseInvoice> list() {
        return em.createQuery(
                        "from PurchaseInvoice i where i.organizationId=:org order by i.createdAt desc",
                        PurchaseInvoice.class)
                .setParameter("org", TenantContext.getOrganizationId())
                .getResultList();
    }

    private PurchaseInvoice create(UUID supplier, UUID po, UUID grn, UUID wh, InvoiceRequest r, List<Line> lines) {
        if (lines == null || lines.isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invoice requires items");
        PurchaseInvoice i = new PurchaseInvoice();
        i.setOrganizationId(TenantContext.getOrganizationId());
        i.setSupplierId(supplier);
        i.setPurchaseOrderId(po);
        i.setGoodsReceiptId(grn);
        i.setWarehouseId(wh);
        i.setInvoiceDate(r.invoiceDate());
        i.setDueDate(r.dueDate());
        i.setSupplierInvoiceNumber(r.supplierInvoiceNumber());
        i.setPlaceOfSupply(r.placeOfSupply());
        i.setTaxInclusive(Boolean.TRUE.equals(r.taxInclusive()));
        i.setNotes(r.notes());
        i.setInvoiceNumber(number(r.invoiceDate()));
        int n = 0;
        for (Line l : lines) {
            PurchaseInvoiceItem x = new PurchaseInvoiceItem();
            x.setInvoice(i);
            x.setProductId(l.productId());
            x.setUnitId(l.unitId());
            x.setDescription(l.description());
            x.setHsnSacCode(l.hsnSacCode());
            x.setQuantity(l.quantity());
            x.setRate(l.rate());
            x.setDiscountPercent(l.discountPercent() == null ? BigDecimal.ZERO : l.discountPercent());
            x.setTaxRate(l.taxRate() == null ? BigDecimal.ZERO : l.taxRate());
            String taxType = TaxSplitDefaults.normalizeTaxType(l.taxType());
            String strategy = TaxSplitDefaults.normalizeStrategy(l.splitStrategy(), taxType);
            x.setTaxType(taxType);
            x.setSplitStrategy(strategy);
            x.setCgstSharePercent(TaxSplitDefaults.cgstShare(strategy, taxType, l.cgstSharePercent()));
            x.setSgstSharePercent(TaxSplitDefaults.sgstShare(strategy, taxType, l.sgstSharePercent()));
            x.setLineOrder(n++);
            BigDecimal discount = l.quantity()
                    .multiply(l.rate())
                    .multiply(x.getDiscountPercent())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            String orgState = organization().getStateCode();
            if (orgState == null || orgState.isBlank()) {
                orgState = "00";
            } else {
                orgState = orgState.trim();
            }
            String place = i.getPlaceOfSupply() == null || i.getPlaceOfSupply().isBlank()
                    ? orgState
                    : i.getPlaceOfSupply().trim();
            GstCalculationDtos.Response tax = gst.calculate(new GstCalculationDtos.Request(
                    orgState,
                    place,
                    x.getTaxRate(),
                    i.isTaxInclusive(),
                    l.quantity(),
                    l.rate(),
                    discount,
                    x.getTaxType(),
                    x.getSplitStrategy(),
                    x.getCgstSharePercent(),
                    x.getSgstSharePercent()));
            x.setDiscountAmount(discount);
            x.setTaxableAmount(tax.taxable());
            x.setCgstAmount(tax.cgst());
            x.setSgstAmount(tax.sgst());
            x.setIgstAmount(tax.igst().add(tax.otherTax()));
            x.setLineTotal(tax.lineTotal());
            i.getItems().add(x);
        }
        i.setSubtotal(i.getItems().stream()
                .map(x -> x.getQuantity().multiply(x.getRate()))
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        i.setTaxableAmount(i.getItems().stream()
                .map(PurchaseInvoiceItem::getTaxableAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        i.setCgstTotal(
                i.getItems().stream().map(PurchaseInvoiceItem::getCgstAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        i.setSgstTotal(
                i.getItems().stream().map(PurchaseInvoiceItem::getSgstAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        i.setIgstTotal(
                i.getItems().stream().map(PurchaseInvoiceItem::getIgstAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        i.setGrandTotal(
                i.getItems().stream().map(PurchaseInvoiceItem::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add));
        i.setOutstandingAmount(i.getGrandTotal());
        em.persist(i);
        searchEvents.upsert(i.getOrganizationId(), SearchEntityType.PURCHASE_INVOICE, i.getId());
        return i;
    }

    private Organization organization() {
        return organizations.findById(TenantContext.getOrganizationId()).orElseThrow();
    }

    private String number(LocalDate d) {
        Organization o = organization();
        return numbers.next(
                o.getId(),
                "PURCHASE_INVOICE",
                o.getPurchaseInvoicePrefix(),
                "{PREFIX}/{FY}/{SEQ:6}",
                o.getFinancialYearStart(),
                d);
    }
}
