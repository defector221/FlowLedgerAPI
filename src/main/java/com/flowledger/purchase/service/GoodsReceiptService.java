package com.flowledger.purchase.service;

import com.flowledger.common.tenant.TenantContext;
import com.flowledger.common.util.DocumentNumberService;
import com.flowledger.inventory.service.InventoryService;
import com.flowledger.organization.entity.Organization;
import com.flowledger.organization.repository.OrganizationRepository;
import com.flowledger.purchase.dto.PurchaseDtos.GrnRequest;
import com.flowledger.purchase.dto.PurchaseDtos.Line;
import com.flowledger.purchase.entity.GoodsReceipt;
import com.flowledger.purchase.entity.GoodsReceiptItem;
import com.flowledger.purchase.entity.PurchaseOrder;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class GoodsReceiptService {
    @PersistenceContext
    private EntityManager em;

    private final PurchaseOrderService orders;
    private final DocumentNumberService numbers;
    private final OrganizationRepository organizations;
    private final InventoryService inventory;

    public GoodsReceiptService(
            PurchaseOrderService o, DocumentNumberService n, OrganizationRepository r, InventoryService i) {
        orders = o;
        numbers = n;
        organizations = r;
        inventory = i;
    }

    public GoodsReceipt fromPurchaseOrder(UUID poId, GrnRequest r) {
        PurchaseOrder po = orders.get(poId);
        GoodsReceipt existing = em.createQuery(
                        "from GoodsReceipt g where g.organizationId=:org and g.purchaseOrderId=:po", GoodsReceipt.class)
                .setParameter("org", TenantContext.getOrganizationId())
                .setParameter("po", poId)
                .getResultStream()
                .findFirst()
                .orElse(null);
        if (existing != null) return existing;
        GoodsReceipt g = new GoodsReceipt();
        g.setOrganizationId(TenantContext.getOrganizationId());
        g.setPurchaseOrderId(poId);
        g.setSupplierId(po.getSupplierId());
        g.setWarehouseId(r.warehouseId());
        g.setReceiptDate(r.receiptDate());
        g.setNotes(r.notes());
        g.setGrnNumber(number(r.receiptDate()));
        List<Line> source = r.items() == null || r.items().isEmpty()
                ? po.getItems().stream()
                        .map(x -> new Line(
                                x.getProductId(),
                                x.getUnitId(),
                                x.getDescription(),
                                null,
                                x.getQuantity(),
                                x.getRate(),
                                null,
                                null,
                                null,
                                null,
                                null,
                                null))
                        .toList()
                : r.items();
        int ix = 0;
        for (Line x : source) {
            GoodsReceiptItem item = new GoodsReceiptItem();
            item.setReceipt(g);
            item.setProductId(x.productId());
            item.setUnitId(x.unitId());
            item.setDescription(x.description());
            item.setQuantity(x.quantity());
            item.setLineOrder(ix++);
            g.getItems().add(item);
        }
        em.persist(g);
        return g;
    }

    public GoodsReceipt confirm(UUID id) {
        GoodsReceipt g = get(id);
        if ("CANCELLED".equals(g.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cancelled GRN cannot be confirmed");
        if (g.isInventoryPosted()) return g;
        for (GoodsReceiptItem item : g.getItems())
            inventory.postPurchase(
                    g.getWarehouseId(),
                    item.getProductId(),
                    item.getQuantity(),
                    BigDecimal.ZERO,
                    g.getReceiptDate(),
                    g.getId(),
                    g.getGrnNumber(),
                    "grn:" + g.getId() + ":" + item.getId());
        g.setInventoryPosted(true);
        g.setStatus("CONFIRMED");
        return g;
    }

    public GoodsReceipt cancel(UUID id) {
        GoodsReceipt g = get(id);
        if ("CANCELLED".equals(g.getStatus())) return g;
        if (g.isInventoryPosted() || "CONFIRMED".equals(g.getStatus())) {
            for (GoodsReceiptItem item : g.getItems())
                inventory.postPurchaseReturn(
                        g.getWarehouseId(),
                        item.getProductId(),
                        item.getQuantity(),
                        BigDecimal.ZERO,
                        LocalDate.now(),
                        g.getId(),
                        g.getGrnNumber(),
                        "grn-cancel:" + g.getId() + ":" + item.getId());
            g.setInventoryPosted(false);
        }
        g.setStatus("CANCELLED");
        return g;
    }

    public GoodsReceipt get(UUID id) {
        GoodsReceipt g = em.find(GoodsReceipt.class, id);
        if (g == null || !g.getOrganizationId().equals(TenantContext.getOrganizationId()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "GRN not found");
        return g;
    }

    public List<GoodsReceipt> list() {
        return em.createQuery(
                        "from GoodsReceipt g where g.organizationId=:org order by g.createdAt desc", GoodsReceipt.class)
                .setParameter("org", TenantContext.getOrganizationId())
                .getResultList();
    }

    private String number(LocalDate d) {
        Organization o =
                organizations.findById(TenantContext.getOrganizationId()).orElseThrow();
        return numbers.next(o.getId(), "GOODS_RECEIPT", "GRN", "{PREFIX}/{FY}/{SEQ:6}", o.getFinancialYearStart(), d);
    }
}
