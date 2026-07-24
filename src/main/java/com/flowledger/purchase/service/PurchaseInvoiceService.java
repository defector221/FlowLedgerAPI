package com.flowledger.purchase.service;

import com.flowledger.accounting.domain.AccountingStatus;
import com.flowledger.accounting.domain.JournalSource;
import com.flowledger.common.dto.PageResponse;
import com.flowledger.common.tenant.TenantContext;
import com.flowledger.common.util.DocumentNumberService;
import com.flowledger.finance.voucher.adapter.DocumentVoucherFacade;
import com.flowledger.finance.voucher.adapter.PurchaseVoucherBuilder;
import com.flowledger.organization.entity.Organization;
import com.flowledger.organization.repository.OrganizationRepository;
import com.flowledger.purchase.dto.PurchaseDtos.InvoiceRequest;
import com.flowledger.purchase.dto.PurchaseDtos.Line;
import com.flowledger.purchase.dto.PurchaseDtos.LineProgress;
import com.flowledger.purchase.dto.PurchaseDtos.OrderFulfillment;
import com.flowledger.purchase.entity.GoodsReceipt;
import com.flowledger.purchase.entity.GoodsReceiptItem;
import com.flowledger.purchase.entity.PurchaseInvoice;
import com.flowledger.purchase.entity.PurchaseInvoiceItem;
import com.flowledger.purchase.entity.PurchaseOrder;
import com.flowledger.purchase.entity.PurchaseOrderItem;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Pageable;
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
        if (!"CONFIRMED".equals(goodsReceipt.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "GRN must be confirmed before invoicing");
        }
        PurchaseInvoice duplicate = em.createQuery(
                        "from PurchaseInvoice i where i.organizationId=:org and i.goodsReceiptId=:grn and i.status <> 'CANCELLED'",
                        PurchaseInvoice.class)
                .setParameter("org", TenantContext.getOrganizationId())
                .setParameter("grn", grnId)
                .getResultStream()
                .findFirst()
                .orElse(null);
        if (duplicate != null) return duplicate;
        List<Line> lines = resolveGrnInvoiceLines(goodsReceipt, request.items());
        return create(
                goodsReceipt.getSupplierId(),
                goodsReceipt.getPurchaseOrderId(),
                grnId,
                goodsReceipt.getWarehouseId(),
                request,
                lines);
    }

    public PurchaseInvoice fromPo(UUID poId, InvoiceRequest request) {
        PurchaseOrder purchaseOrder = orders.get(poId);
        if (!"CONFIRMED".equals(purchaseOrder.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Purchase order must be confirmed before invoicing");
        List<Line> lines = resolveOrderInvoiceLines(purchaseOrder, request.items(), null);
        return create(purchaseOrder.getSupplierId(), poId, null, null, request, lines);
    }

    public PurchaseInvoice update(UUID id, InvoiceRequest request) {
        PurchaseInvoice invoice = get(id);
        if (!"DRAFT".equals(invoice.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only draft invoices can be updated");
        List<Line> lines = resolveLinesForExistingInvoice(invoice, request.items());
        applyRequest(invoice, request, lines);
        searchEvents.upsert(invoice.getOrganizationId(), SearchEntityType.PURCHASE_INVOICE, invoice.getId());
        return invoice;
    }

    private List<Line> resolveLinesForExistingInvoice(PurchaseInvoice invoice, List<Line> requestItems) {
        if (invoice.getGoodsReceiptId() != null) {
            return resolveGrnInvoiceLines(grns.get(invoice.getGoodsReceiptId()), requestItems);
        }
        if (invoice.getPurchaseOrderId() != null) {
            return resolveOrderInvoiceLines(orders.get(invoice.getPurchaseOrderId()), requestItems, invoice.getId());
        }
        if (requestItems == null || requestItems.isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invoice requires items");
        return requestItems;
    }

    /** Default/validate invoice lines against remaining billable qty for a PO-sourced invoice. */
    private List<Line> resolveOrderInvoiceLines(PurchaseOrder po, List<Line> requestItems, UUID excludeInvoiceId) {
        Map<UUID, BigDecimal> billable = billableByProduct(po, excludeInvoiceId);
        if (requestItems == null || requestItems.isEmpty()) {
            return po.getItems().stream()
                    .map(item -> new Line(
                            item.getProductId(),
                            item.getUnitId(),
                            item.getDescription(),
                            item.getHsnSacCode(),
                            billable.getOrDefault(item.getProductId(), BigDecimal.ZERO),
                            item.getRate(),
                            item.getDiscountPercent(),
                            item.getTaxRate(),
                            item.getTaxType(),
                            item.getSplitStrategy(),
                            item.getCgstSharePercent(),
                            item.getSgstSharePercent()))
                    .filter(line -> line.quantity() != null && line.quantity().signum() > 0)
                    .toList();
        }
        Set<UUID> poProductIds =
                po.getItems().stream().map(PurchaseOrderItem::getProductId).collect(Collectors.toSet());
        Map<UUID, BigDecimal> requestedByProduct = new HashMap<>();
        for (Line line : requestItems) {
            if (!poProductIds.contains(line.productId()))
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Product not part of purchase order: " + line.productId());
            requestedByProduct.merge(line.productId(), line.quantity(), BigDecimal::add);
        }
        for (var entry : requestedByProduct.entrySet()) {
            BigDecimal avail = billable.getOrDefault(entry.getKey(), BigDecimal.ZERO);
            if (entry.getValue().compareTo(avail) > 0)
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Quantity exceeds billable qty for product " + entry.getKey() + " (billable: " + avail + ")");
        }
        return requestItems;
    }

    /** Default/validate invoice lines against a GRN's received qty. */
    private List<Line> resolveGrnInvoiceLines(GoodsReceipt goodsReceipt, List<Line> requestItems) {
        if (requestItems == null || requestItems.isEmpty()) {
            return goodsReceipt.getItems().stream()
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
                    .toList();
        }
        Map<UUID, BigDecimal> grnQtyByProduct = new HashMap<>();
        for (GoodsReceiptItem item : goodsReceipt.getItems()) {
            grnQtyByProduct.merge(item.getProductId(), item.getQuantity(), BigDecimal::add);
        }
        Map<UUID, BigDecimal> requestedByProduct = new HashMap<>();
        for (Line line : requestItems) {
            if (!grnQtyByProduct.containsKey(line.productId()))
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Product not part of GRN: " + line.productId());
            requestedByProduct.merge(line.productId(), line.quantity(), BigDecimal::add);
        }
        for (var entry : requestedByProduct.entrySet()) {
            BigDecimal avail = grnQtyByProduct.getOrDefault(entry.getKey(), BigDecimal.ZERO);
            if (entry.getValue().compareTo(avail) > 0)
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Quantity exceeds GRN received qty for product " + entry.getKey() + " (received: " + avail
                                + ")");
        }
        return requestItems;
    }

    /** Sum of non-cancelled invoice quantities per product for a PO, optionally excluding one invoice. */
    public Map<UUID, BigDecimal> billedByProduct(UUID poId, UUID excludeInvoiceId) {
        List<PurchaseInvoice> nonCancelled = em.createQuery(
                        "from PurchaseInvoice i where i.organizationId=:org and i.purchaseOrderId=:po and i.status <> 'CANCELLED'",
                        PurchaseInvoice.class)
                .setParameter("org", TenantContext.getOrganizationId())
                .setParameter("po", poId)
                .getResultList();
        Map<UUID, BigDecimal> billed = new HashMap<>();
        for (PurchaseInvoice invoice : nonCancelled) {
            if (excludeInvoiceId != null && excludeInvoiceId.equals(invoice.getId())) continue;
            for (PurchaseInvoiceItem item : invoice.getItems()) {
                billed.merge(item.getProductId(), item.getQuantity(), BigDecimal::add);
            }
        }
        return billed;
    }

    private Map<UUID, BigDecimal> billableByProduct(PurchaseOrder po, UUID excludeInvoiceId) {
        Map<UUID, BigDecimal> billed = billedByProduct(po.getId(), excludeInvoiceId);
        Map<UUID, BigDecimal> billable = new HashMap<>();
        for (PurchaseOrderItem item : po.getItems()) {
            BigDecimal remaining =
                    item.getQuantity().subtract(billed.getOrDefault(item.getProductId(), BigDecimal.ZERO));
            billable.merge(item.getProductId(), remaining, BigDecimal::add);
        }
        return billable;
    }

    @Transactional(readOnly = true)
    public OrderFulfillment fulfillment(UUID poId) {
        PurchaseOrder po = orders.get(poId);
        Map<UUID, BigDecimal> received = grns.receivedByProduct(poId, null);
        Map<UUID, BigDecimal> billed = billedByProduct(poId, null);
        List<LineProgress> lines = po.getItems().stream()
                .map(item -> {
                    BigDecimal ordered = item.getQuantity();
                    BigDecimal receivedQty = received.getOrDefault(item.getProductId(), BigDecimal.ZERO);
                    BigDecimal invoicedQty = billed.getOrDefault(item.getProductId(), BigDecimal.ZERO);
                    return new LineProgress(
                            item.getProductId(),
                            item.getUnitId(),
                            item.getDescription(),
                            ordered,
                            receivedQty,
                            invoicedQty,
                            ordered.subtract(receivedQty),
                            ordered.subtract(invoicedQty));
                })
                .toList();
        List<UUID> grnIds =
                grns.listByPurchaseOrder(poId).stream().map(GoodsReceipt::getId).toList();
        List<UUID> invoiceIds =
                listByPurchaseOrder(poId).stream().map(PurchaseInvoice::getId).toList();
        return new OrderFulfillment(po.getId(), po.getPoNumber(), po.getStatus(), lines, grnIds, invoiceIds);
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

    public PageResponse<PurchaseInvoice> list(Pageable pageable, UUID purchaseOrderId, UUID goodsReceiptId) {
        UUID org = TenantContext.getOrganizationId();
        String where = "i.organizationId=:org"
                + (purchaseOrderId != null ? " and i.purchaseOrderId=:po" : "")
                + (goodsReceiptId != null ? " and i.goodsReceiptId=:grn" : "");
        var countQ = em.createQuery("select count(i) from PurchaseInvoice i where " + where, Long.class)
                .setParameter("org", org);
        var listQ = em.createQuery(
                        "from PurchaseInvoice i where " + where + " order by i.createdAt desc", PurchaseInvoice.class)
                .setParameter("org", org);
        if (purchaseOrderId != null) {
            countQ.setParameter("po", purchaseOrderId);
            listQ.setParameter("po", purchaseOrderId);
        }
        if (goodsReceiptId != null) {
            countQ.setParameter("grn", goodsReceiptId);
            listQ.setParameter("grn", goodsReceiptId);
        }
        long total = countQ.getSingleResult();
        List<PurchaseInvoice> content = listQ.setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize())
                .getResultList();
        return PageResponse.of(content, pageable, total);
    }

    public PageResponse<PurchaseInvoice> list(Pageable pageable) {
        return list(pageable, null, null);
    }

    public List<PurchaseInvoice> listByPurchaseOrder(UUID poId) {
        return em.createQuery(
                        "from PurchaseInvoice i where i.organizationId=:org and i.purchaseOrderId=:po order by i.createdAt desc",
                        PurchaseInvoice.class)
                .setParameter("org", TenantContext.getOrganizationId())
                .setParameter("po", poId)
                .getResultList();
    }

    public List<PurchaseInvoice> listByGoodsReceipt(UUID grnId) {
        return em.createQuery(
                        "from PurchaseInvoice i where i.organizationId=:org and i.goodsReceiptId=:grn order by i.createdAt desc",
                        PurchaseInvoice.class)
                .setParameter("org", TenantContext.getOrganizationId())
                .setParameter("grn", grnId)
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
        invoice.setInvoiceNumber(number(request.invoiceDate()));
        applyRequest(invoice, request, lines);
        em.persist(invoice);
        searchEvents.upsert(invoice.getOrganizationId(), SearchEntityType.PURCHASE_INVOICE, invoice.getId());
        return invoice;
    }

    private void applyRequest(PurchaseInvoice invoice, InvoiceRequest request, List<Line> lines) {
        if (lines == null || lines.isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invoice requires items");
        invoice.setInvoiceDate(request.invoiceDate());
        invoice.setDueDate(request.dueDate());
        invoice.setSupplierInvoiceNumber(request.supplierInvoiceNumber());
        invoice.setPlaceOfSupply(request.placeOfSupply());
        invoice.setTaxInclusive(Boolean.TRUE.equals(request.taxInclusive()));
        invoice.setNotes(request.notes());
        invoice.getItems().clear();
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
