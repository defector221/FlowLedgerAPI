package com.flowledger.purchase.service;

import com.flowledger.common.tenant.TenantContext;
import com.flowledger.common.util.DocumentNumberService;
import com.flowledger.organization.entity.Organization;
import com.flowledger.organization.repository.OrganizationRepository;
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

    public PurchaseOrderService(DocumentNumberService n, OrganizationRepository o) {
        numbers = n;
        organizations = o;
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
            PurchaseOrderItem item = new PurchaseOrderItem();
            item.setOrder(po);
            item.setProductId(line.productId());
            item.setUnitId(line.unitId());
            item.setDescription(line.description());
            item.setHsnSacCode(line.hsnSacCode());
            item.setQuantity(line.quantity());
            item.setRate(line.rate());
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

    private void calculate(PurchaseOrderItem x) {
        BigDecimal gross = x.getQuantity().multiply(x.getRate());
        x.setDiscountAmount(
                gross.multiply(x.getDiscountPercent()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
        x.setTaxableAmount(gross.subtract(x.getDiscountAmount()));
        BigDecimal tax =
                x.getTaxableAmount().multiply(x.getTaxRate()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        String strategy = TaxSplitDefaults.normalizeStrategy(x.getSplitStrategy(), x.getTaxType());
        switch (strategy) {
            case "NO_SPLIT_IGST", "NO_SPLIT_OTHER" -> {
                x.setCgstAmount(BigDecimal.ZERO);
                x.setSgstAmount(BigDecimal.ZERO);
                x.setIgstAmount(tax);
            }
            default -> {
                BigDecimal cgstShare = x.getCgstSharePercent() == null ? new BigDecimal("50") : x.getCgstSharePercent();
                x.setCgstAmount(tax.multiply(cgstShare).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
                x.setSgstAmount(tax.subtract(x.getCgstAmount()));
                x.setIgstAmount(BigDecimal.ZERO);
            }
        }
        x.setLineTotal(x.getTaxableAmount().add(tax));
    }

    private void totals(PurchaseOrder po) {
        po.setSubtotal(po.getItems().stream()
                .map(i -> i.getQuantity().multiply(i.getRate()))
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        po.setDiscountTotal(po.getItems().stream()
                .map(PurchaseOrderItem::getDiscountAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        po.setTaxTotal(po.getItems().stream()
                .map(i -> i.getCgstAmount().add(i.getSgstAmount()).add(i.getIgstAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        po.setGrandTotal(
                po.getItems().stream().map(PurchaseOrderItem::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private String number(String type, String prefix, LocalDate date) {
        Organization o =
                organizations.findById(TenantContext.getOrganizationId()).orElseThrow();
        return numbers.next(o.getId(), type, prefix, "{PREFIX}/{FY}/{SEQ:6}", o.getFinancialYearStart(), date);
    }

    private PurchaseOrder owned(PurchaseOrder p) {
        if (!p.getOrganizationId().equals(TenantContext.getOrganizationId())) throw missing("Purchase order");
        return p;
    }

    private ResponseStatusException missing(String s) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, s + " not found");
    }

    private ResponseStatusException conflict(String s) {
        return new ResponseStatusException(HttpStatus.CONFLICT, s);
    }

    private static BigDecimal z(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
