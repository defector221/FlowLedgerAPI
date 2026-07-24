package com.flowledger.purchase.service;

import com.flowledger.accounting.domain.AccountingStatus;
import com.flowledger.accounting.domain.JournalSource;
import com.flowledger.common.tenant.TenantContext;
import com.flowledger.common.util.DocumentNumberService;
import com.flowledger.finance.voucher.adapter.DocumentVoucherFacade;
import com.flowledger.finance.voucher.adapter.PurchaseVoucherBuilder;
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
    private final DocumentVoucherFacade documentPosting;

    public PurchaseInvoiceService(
            GoodsReceiptService goodsReceiptService,
            PurchaseOrderService purchaseOrderService,
            DocumentNumberService documentNumberService,
            OrganizationRepository organizationRepository,
            GstCalculationService tax,
            SearchIndexEventPublisher searchEvents,
            DocumentVoucherFacade documentPosting) {
        grns = goodsReceiptService;
        orders = purchaseOrderService;
        numbers = documentNumberService;
        organizations = organizationRepository;
        gst = tax;
        this.searchEvents = searchEvents;
        this.documentPosting = documentPosting;
    }

    public PurchaseInvoice fromGrn(UUID grnId, InvoiceRequest request) {
        GoodsReceipt goodsReceipt = grns.get(grnId);
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
                goodsReceipt.getSupplierId(),
                goodsReceipt.getPurchaseOrderId(),
                grnId,
                goodsReceipt.getWarehouseId(),
                request,
                goodsReceipt.getItems().stream()
                        .map(item -> new Line(
                                item.getProductId(),
                                item.getUnitId(),
                                item.getDescription(),
                                null,
                                item.getQuantity(),
                                BigDecimal.ZERO,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null))
                        .toList());
    }

    public PurchaseInvoice fromPo(UUID poId, InvoiceRequest request) {
        PurchaseOrder purchaseOrder = orders.get(poId);
        return create(
                purchaseOrder.getSupplierId(),
                poId,
                null,
                null,
                request,
                request.items() == null
                        ? purchaseOrder.getItems().stream()
                                .map(item -> new Line(
                                        item.getProductId(),
                                        item.getUnitId(),
                                        item.getDescription(),
                                        item.getHsnSacCode(),
                                        item.getQuantity(),
                                        item.getRate(),
                                        item.getDiscountPercent(),
                                        item.getTaxRate(),
                                        item.getTaxType(),
                                        item.getSplitStrategy(),
                                        item.getCgstSharePercent(),
                                        item.getSgstSharePercent()))
                                .toList()
                        : request.items());
    }

    public PurchaseInvoice confirm(UUID id) {
        PurchaseInvoice invoice = get(id);
        if ("CANCELLED".equals(invoice.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cancelled invoice cannot be confirmed");
        if (!"DRAFT".equals(invoice.getStatus())) return invoice;
        invoice.setStatus(invoice.getOutstandingAmount().signum() == 0 ? "PAID" : "CONFIRMED");
        documentPosting.postPurchaseInvoice(invoice);
        searchEvents.upsert(invoice.getOrganizationId(), SearchEntityType.PURCHASE_INVOICE, invoice.getId());
        return invoice;
    }

    public PurchaseInvoice cancel(UUID id) {
        PurchaseInvoice invoice = get(id);
        if ("CANCELLED".equals(invoice.getStatus())) return invoice;
        if (invoice.getAmountPaid() != null && invoice.getAmountPaid().signum() > 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Paid invoice cannot be cancelled");
        invoice.setStatus("CANCELLED");
        if (invoice.getAccountingStatus() == AccountingStatus.POSTED) {
            documentPosting.reverseDocument(
                    invoice.getOrganizationId(),
                    PurchaseVoucherBuilder.REFERENCE_TYPE,
                    invoice.getId(),
                    JournalSource.PURCHASE_INVOICE);
            invoice.setAccountingStatus(AccountingStatus.REVERSED);
        }
        searchEvents.upsert(invoice.getOrganizationId(), SearchEntityType.PURCHASE_INVOICE, invoice.getId());
        return invoice;
    }

    public PurchaseInvoice get(UUID id) {
        PurchaseInvoice invoice = em.find(PurchaseInvoice.class, id);
        if (invoice == null || !invoice.getOrganizationId().equals(TenantContext.getOrganizationId()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Purchase invoice not found");
        return invoice;
    }

    public List<PurchaseInvoice> list() {
        return em.createQuery(
                        "from PurchaseInvoice i where i.organizationId=:org order by i.createdAt desc",
                        PurchaseInvoice.class)
                .setParameter("org", TenantContext.getOrganizationId())
                .getResultList();
    }

    private PurchaseInvoice create(
            UUID supplier, UUID po, UUID grn, UUID wh, InvoiceRequest request, List<Line> lines) {
        if (lines == null || lines.isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invoice requires items");
        PurchaseInvoice invoice = new PurchaseInvoice();
        invoice.setOrganizationId(TenantContext.getOrganizationId());
        invoice.setSupplierId(supplier);
        invoice.setPurchaseOrderId(po);
        invoice.setGoodsReceiptId(grn);
        invoice.setWarehouseId(wh);
        invoice.setInvoiceDate(request.invoiceDate());
        invoice.setDueDate(request.dueDate());
        invoice.setSupplierInvoiceNumber(request.supplierInvoiceNumber());
        invoice.setPlaceOfSupply(request.placeOfSupply());
        invoice.setTaxInclusive(Boolean.TRUE.equals(request.taxInclusive()));
        invoice.setNotes(request.notes());
        invoice.setInvoiceNumber(number(request.invoiceDate()));
        int n = 0;
        for (Line line : lines) {
            PurchaseInvoiceItem item = new PurchaseInvoiceItem();
            item.setInvoice(invoice);
            item.setProductId(line.productId());
            item.setUnitId(line.unitId());
            item.setDescription(line.description());
            item.setHsnSacCode(line.hsnSacCode());
            item.setQuantity(line.quantity());
            item.setRate(line.rate());
            item.setDiscountPercent(line.discountPercent() == null ? BigDecimal.ZERO : line.discountPercent());
            item.setTaxRate(line.taxRate() == null ? BigDecimal.ZERO : line.taxRate());
            String taxType = TaxSplitDefaults.normalizeTaxType(line.taxType());
            String strategy = TaxSplitDefaults.normalizeStrategy(line.splitStrategy(), taxType);
            item.setTaxType(taxType);
            item.setSplitStrategy(strategy);
            item.setCgstSharePercent(TaxSplitDefaults.cgstShare(strategy, taxType, line.cgstSharePercent()));
            item.setSgstSharePercent(TaxSplitDefaults.sgstShare(strategy, taxType, line.sgstSharePercent()));
            item.setLineOrder(n++);
            BigDecimal discount = line.quantity()
                    .multiply(line.rate())
                    .multiply(item.getDiscountPercent())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            String orgState = organization().getStateCode();
            if (orgState == null || orgState.isBlank()) {
                orgState = "00";
            } else {
                orgState = orgState.trim();
            }
            String place = invoice.getPlaceOfSupply() == null
                            || invoice.getPlaceOfSupply().isBlank()
                    ? orgState
                    : invoice.getPlaceOfSupply().trim();
            GstCalculationDtos.Response tax = gst.calculate(new GstCalculationDtos.Request(
                    orgState,
                    place,
                    item.getTaxRate(),
                    invoice.isTaxInclusive(),
                    line.quantity(),
                    line.rate(),
                    discount,
                    item.getTaxType(),
                    item.getSplitStrategy(),
                    item.getCgstSharePercent(),
                    item.getSgstSharePercent()));
            item.setDiscountAmount(discount);
            item.setTaxableAmount(tax.taxable());
            item.setCgstAmount(tax.cgst());
            item.setSgstAmount(tax.sgst());
            item.setIgstAmount(tax.igst().add(tax.otherTax()));
            item.setLineTotal(tax.lineTotal());
            invoice.getItems().add(item);
        }
        invoice.setSubtotal(invoice.getItems().stream()
                .map(item -> item.getQuantity().multiply(item.getRate()))
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        invoice.setTaxableAmount(invoice.getItems().stream()
                .map(PurchaseInvoiceItem::getTaxableAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        invoice.setCgstTotal(invoice.getItems().stream()
                .map(PurchaseInvoiceItem::getCgstAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        invoice.setSgstTotal(invoice.getItems().stream()
                .map(PurchaseInvoiceItem::getSgstAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        invoice.setIgstTotal(invoice.getItems().stream()
                .map(PurchaseInvoiceItem::getIgstAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        invoice.setGrandTotal(invoice.getItems().stream()
                .map(PurchaseInvoiceItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        invoice.setOutstandingAmount(invoice.getGrandTotal());
        em.persist(invoice);
        searchEvents.upsert(invoice.getOrganizationId(), SearchEntityType.PURCHASE_INVOICE, invoice.getId());
        return invoice;
    }

    private Organization organization() {
        return organizations.findById(TenantContext.getOrganizationId()).orElseThrow();
    }

    private String number(LocalDate date) {
        Organization organization = organization();
        return numbers.next(
                organization.getId(),
                "PURCHASE_INVOICE",
                organization.getPurchaseInvoicePrefix(),
                "{PREFIX}/{FY}/{SEQ:6}",
                organization.getFinancialYearStart(),
                date);
    }
}
