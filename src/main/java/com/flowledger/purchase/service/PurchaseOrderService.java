package com.flowledger.purchase.service;

import com.flowledger.common.tenant.TenantContext;
import com.flowledger.common.util.DocumentNumberService;
import com.flowledger.organization.entity.Organization;
import com.flowledger.organization.repository.OrganizationRepository;
import com.flowledger.product.service.SupplierCatalogService;
import com.flowledger.purchase.dto.PurchaseDtos.Line;
import com.flowledger.purchase.dto.PurchaseDtos.OrderRequest;
import com.flowledger.purchase.entity.PurchaseOrder;
import com.flowledger.purchase.entity.PurchaseOrderItem;
import com.flowledger.tax.TaxSplitDefaults;
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
public class PurchaseOrderService {
    @PersistenceContext
    private EntityManager em;

    private final DocumentNumberService numbers;
    private final OrganizationRepository organizations;
    private final SupplierCatalogService supplierCatalog;

    public PurchaseOrderService(
            DocumentNumberService documentNumberService,
            OrganizationRepository organizationRepository,
            SupplierCatalogService supplierCatalog) {
        numbers = documentNumberService;
        organizations = organizationRepository;
        this.supplierCatalog = supplierCatalog;
    }

    public PurchaseOrder create(OrderRequest request) {
        PurchaseOrder po = new PurchaseOrder();
        po.setOrganizationId(TenantContext.getOrganizationId());
        po.setSupplierId(request.supplierId());
        po.setOrderDate(request.orderDate());
        po.setExpectedDeliveryDate(request.expectedDeliveryDate());
        po.setNotes(request.notes());
        po.setTermsAndConditions(request.termsAndConditions());
        po.setPoNumber(number("PURCHASE_ORDER", "PO", request.orderDate()));
        applyOrderLines(po, request.items());
        em.persist(po);
        return po;
    }

    public PurchaseOrder get(UUID id) {
        PurchaseOrder found = em.find(PurchaseOrder.class, id);
        if (found == null) throw missing("Purchase order");
        return owned(found);
    }

    public List<PurchaseOrder> list() {
        return em.createQuery(
                        "from PurchaseOrder p where p.organizationId=:org order by p.createdAt desc",
                        PurchaseOrder.class)
                .setParameter("org", TenantContext.getOrganizationId())
                .getResultList();
    }

    public PurchaseOrder update(UUID id, OrderRequest request) {
        PurchaseOrder po = get(id);
        if (!"DRAFT".equals(po.getStatus())) throw conflict("Only draft orders can be changed");
        po.setSupplierId(request.supplierId());
        po.setOrderDate(request.orderDate());
        po.setExpectedDeliveryDate(request.expectedDeliveryDate());
        po.setNotes(request.notes());
        po.setTermsAndConditions(request.termsAndConditions());
        po.getItems().clear();
        applyOrderLines(po, request.items());
        return po;
    }

    public void delete(UUID id) {
        PurchaseOrder po = get(id);
        if (!"DRAFT".equals(po.getStatus())) throw conflict("Only draft orders can be deleted");
        po.setStatus("CANCELLED");
    }

    public PurchaseOrder confirm(UUID id) {
        PurchaseOrder po = get(id);
        if ("CONFIRMED".equals(po.getStatus())) return po;
        if (!"DRAFT".equals(po.getStatus())) throw conflict("Only draft orders can be confirmed");
        po.setStatus("CONFIRMED");
        return po;
    }

    public PurchaseOrder cancel(UUID id) {
        PurchaseOrder po = get(id);
        if ("CANCELLED".equals(po.getStatus())) return po;
        long invoiced = em.createQuery(
                        """
                        select count(i) from PurchaseInvoice i
                        where i.organizationId=:org and i.purchaseOrderId=:po
                          and i.status <> 'CANCELLED'
                        """,
                        Long.class)
                .setParameter("org", TenantContext.getOrganizationId())
                .setParameter("po", id)
                .getSingleResult();
        if (invoiced > 0) throw conflict("Order already invoiced and cannot be cancelled");
        po.setStatus("CANCELLED");
        return po;
    }

    private void applyOrderLines(PurchaseOrder po, List<Line> lines) {
        int i = 0;
        for (Line line : lines) {
            var catalogItem = supplierCatalog.requireActiveCatalogItem(
                    TenantContext.getOrganizationId(), po.getSupplierId(), line.productId());
            PurchaseOrderItem item = new PurchaseOrderItem();
            item.setOrder(po);
            item.setProductId(line.productId());
            item.setUnitId(line.unitId());
            item.setDescription(line.description());
            item.setHsnSacCode(line.hsnSacCode());
            item.setQuantity(line.quantity());
            BigDecimal rate = line.rate();
            if (rate == null || rate.signum() == 0) {
                rate = catalogItem.getPurchasePrice();
            }
            item.setRate(rate);
            item.setDiscountPercent(z(line.discountPercent()));
            item.setTaxRate(z(line.taxRate()));
            String taxType = TaxSplitDefaults.normalizeTaxType(line.taxType());
            String strategy = TaxSplitDefaults.normalizeStrategy(line.splitStrategy(), taxType);
            item.setTaxType(taxType);
            item.setSplitStrategy(strategy);
            item.setCgstSharePercent(TaxSplitDefaults.cgstShare(strategy, taxType, line.cgstSharePercent()));
            item.setSgstSharePercent(TaxSplitDefaults.sgstShare(strategy, taxType, line.sgstSharePercent()));
            item.setLineOrder(i++);
            calculate(item);
            po.getItems().add(item);
        }
        totals(po);
    }

    private void calculate(PurchaseOrderItem item) {
        BigDecimal gross = item.getQuantity().multiply(item.getRate());
        item.setDiscountAmount(
                gross.multiply(item.getDiscountPercent()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
        item.setTaxableAmount(gross.subtract(item.getDiscountAmount()));
        BigDecimal tax = item.getTaxableAmount()
                .multiply(item.getTaxRate())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        String strategy = TaxSplitDefaults.normalizeStrategy(item.getSplitStrategy(), item.getTaxType());
        switch (strategy) {
            case "NO_SPLIT_IGST", "NO_SPLIT_OTHER" -> {
                item.setCgstAmount(BigDecimal.ZERO);
                item.setSgstAmount(BigDecimal.ZERO);
                item.setIgstAmount(tax);
            }
            default -> {
                BigDecimal cgstShare =
                        item.getCgstSharePercent() == null ? new BigDecimal("50") : item.getCgstSharePercent();
                item.setCgstAmount(tax.multiply(cgstShare).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
                item.setSgstAmount(tax.subtract(item.getCgstAmount()));
                item.setIgstAmount(BigDecimal.ZERO);
            }
        }
        item.setLineTotal(item.getTaxableAmount().add(tax));
    }

    private void totals(PurchaseOrder po) {
        po.setSubtotal(po.getItems().stream()
                .map(item -> item.getQuantity().multiply(item.getRate()))
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        po.setDiscountTotal(po.getItems().stream()
                .map(PurchaseOrderItem::getDiscountAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        po.setTaxTotal(po.getItems().stream()
                .map(item -> item.getCgstAmount().add(item.getSgstAmount()).add(item.getIgstAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        po.setGrandTotal(
                po.getItems().stream().map(PurchaseOrderItem::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private String number(String type, String prefix, LocalDate date) {
        Organization organization =
                organizations.findById(TenantContext.getOrganizationId()).orElseThrow();
        return numbers.next(
                organization.getId(),
                type,
                prefix,
                "{PREFIX}/{FY}/{SEQ:6}",
                organization.getFinancialYearStart(),
                date);
    }

    private PurchaseOrder owned(PurchaseOrder purchaseOrder) {
        if (!purchaseOrder.getOrganizationId().equals(TenantContext.getOrganizationId()))
            throw missing("Purchase order");
        return purchaseOrder;
    }

    private ResponseStatusException missing(String label) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, label + " not found");
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private static BigDecimal z(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
