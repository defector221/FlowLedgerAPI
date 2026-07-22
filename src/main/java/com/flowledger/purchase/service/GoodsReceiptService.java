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
            PurchaseOrderService purchaseOrderService,
            DocumentNumberService documentNumberService,
            OrganizationRepository organizationRepository,
            InventoryService inventoryService) {
        orders = purchaseOrderService;
        numbers = documentNumberService;
        organizations = organizationRepository;
        inventory = inventoryService;
    }

    public GoodsReceipt fromPurchaseOrder(UUID poId, GrnRequest request) {
        PurchaseOrder po = orders.get(poId);
        GoodsReceipt existing = em.createQuery(
                        "from GoodsReceipt g where g.organizationId=:org and g.purchaseOrderId=:po", GoodsReceipt.class)
                .setParameter("org", TenantContext.getOrganizationId())
                .setParameter("po", poId)
                .getResultStream()
                .findFirst()
                .orElse(null);
        if (existing != null) return existing;
        GoodsReceipt goodsReceipt = new GoodsReceipt();
        goodsReceipt.setOrganizationId(TenantContext.getOrganizationId());
        goodsReceipt.setPurchaseOrderId(poId);
        goodsReceipt.setSupplierId(po.getSupplierId());
        goodsReceipt.setWarehouseId(request.warehouseId());
        goodsReceipt.setReceiptDate(request.receiptDate());
        goodsReceipt.setNotes(request.notes());
        goodsReceipt.setGrnNumber(number(request.receiptDate()));
        List<Line> source = request.items() == null || request.items().isEmpty()
                ? po.getItems().stream()
                        .map(line -> new Line(
                                line.getProductId(),
                                line.getUnitId(),
                                line.getDescription(),
                                null,
                                line.getQuantity(),
                                line.getRate(),
                                null,
                                null,
                                null,
                                null,
                                null,
                                null))
                        .toList()
                : request.items();
        int ix = 0;
        for (Line line : source) {
            GoodsReceiptItem item = new GoodsReceiptItem();
            item.setReceipt(goodsReceipt);
            item.setProductId(line.productId());
            item.setUnitId(line.unitId());
            item.setDescription(line.description());
            item.setQuantity(line.quantity());
            item.setLineOrder(ix++);
            goodsReceipt.getItems().add(item);
        }
        em.persist(goodsReceipt);
        return goodsReceipt;
    }

    public GoodsReceipt confirm(UUID id) {
        GoodsReceipt goodsReceipt = get(id);
        if ("CANCELLED".equals(goodsReceipt.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cancelled GRN cannot be confirmed");
        if (goodsReceipt.isInventoryPosted()) return goodsReceipt;
        for (GoodsReceiptItem item : goodsReceipt.getItems())
            inventory.postPurchase(
                    goodsReceipt.getWarehouseId(),
                    item.getProductId(),
                    item.getQuantity(),
                    BigDecimal.ZERO,
                    goodsReceipt.getReceiptDate(),
                    goodsReceipt.getId(),
                    goodsReceipt.getGrnNumber(),
                    "grn:" + goodsReceipt.getId() + ":" + item.getId());
        goodsReceipt.setInventoryPosted(true);
        goodsReceipt.setStatus("CONFIRMED");
        return goodsReceipt;
    }

    public GoodsReceipt cancel(UUID id) {
        GoodsReceipt goodsReceipt = get(id);
        if ("CANCELLED".equals(goodsReceipt.getStatus())) return goodsReceipt;
        if (goodsReceipt.isInventoryPosted() || "CONFIRMED".equals(goodsReceipt.getStatus())) {
            for (GoodsReceiptItem item : goodsReceipt.getItems())
                inventory.postPurchaseReturn(
                        goodsReceipt.getWarehouseId(),
                        item.getProductId(),
                        item.getQuantity(),
                        BigDecimal.ZERO,
                        LocalDate.now(),
                        goodsReceipt.getId(),
                        goodsReceipt.getGrnNumber(),
                        "grn-cancel:" + goodsReceipt.getId() + ":" + item.getId());
            goodsReceipt.setInventoryPosted(false);
        }
        goodsReceipt.setStatus("CANCELLED");
        return goodsReceipt;
    }

    public GoodsReceipt get(UUID id) {
        GoodsReceipt goodsReceipt = em.find(GoodsReceipt.class, id);
        if (goodsReceipt == null || !goodsReceipt.getOrganizationId().equals(TenantContext.getOrganizationId()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "GRN not found");
        return goodsReceipt;
    }

    public List<GoodsReceipt> list() {
        return em.createQuery(
                        "from GoodsReceipt g where g.organizationId=:org order by g.createdAt desc", GoodsReceipt.class)
                .setParameter("org", TenantContext.getOrganizationId())
                .getResultList();
    }

    private String number(LocalDate date) {
        Organization organization =
                organizations.findById(TenantContext.getOrganizationId()).orElseThrow();
        return numbers.next(
                organization.getId(),
                "GOODS_RECEIPT",
                "GRN",
                "{PREFIX}/{FY}/{SEQ:6}",
                organization.getFinancialYearStart(),
                date);
    }
}
