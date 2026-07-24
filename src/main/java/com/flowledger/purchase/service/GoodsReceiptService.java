package com.flowledger.purchase.service;

import com.flowledger.common.dto.PageResponse;
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
import com.flowledger.purchase.entity.PurchaseOrderItem;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
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
        PurchaseOrder po = requireConfirmedPo(poId);
        Map<UUID, BigDecimal> receivable = receivableByProduct(poId, null);
        List<Line> source = resolveGrnLines(po, request.items(), receivable);
        GoodsReceipt goodsReceipt = new GoodsReceipt();
        goodsReceipt.setOrganizationId(TenantContext.getOrganizationId());
        goodsReceipt.setPurchaseOrderId(poId);
        goodsReceipt.setSupplierId(po.getSupplierId());
        goodsReceipt.setWarehouseId(request.warehouseId());
        goodsReceipt.setReceiptDate(request.receiptDate());
        goodsReceipt.setNotes(request.notes());
        goodsReceipt.setGrnNumber(number(request.receiptDate()));
        applyLines(goodsReceipt, source);
        em.persist(goodsReceipt);
        return goodsReceipt;
    }

    public GoodsReceipt update(UUID id, GrnRequest request) {
        GoodsReceipt goodsReceipt = get(id);
        if (!"DRAFT".equals(goodsReceipt.getStatus())) {
            throw conflict("Only draft GRNs can be changed");
        }
        if (goodsReceipt.getPurchaseOrderId() == null) {
            throw conflict("GRN is not linked to a purchase order");
        }
        PurchaseOrder po = requireConfirmedPo(goodsReceipt.getPurchaseOrderId());
        Map<UUID, BigDecimal> receivable = receivableByProduct(po.getId(), goodsReceipt.getId());
        List<Line> source = resolveGrnLines(po, request.items(), receivable);
        goodsReceipt.setWarehouseId(request.warehouseId());
        goodsReceipt.setReceiptDate(request.receiptDate());
        goodsReceipt.setNotes(request.notes());
        goodsReceipt.getItems().clear();
        applyLines(goodsReceipt, source);
        return goodsReceipt;
    }

    public GoodsReceipt confirm(UUID id) {
        GoodsReceipt goodsReceipt = get(id);
        if ("CANCELLED".equals(goodsReceipt.getStatus())) throw conflict("Cancelled GRN cannot be confirmed");
        if (goodsReceipt.isInventoryPosted()) return goodsReceipt;
        if (goodsReceipt.getPurchaseOrderId() != null) {
            Map<UUID, BigDecimal> receivable =
                    receivableByProduct(goodsReceipt.getPurchaseOrderId(), goodsReceipt.getId());
            for (GoodsReceiptItem item : goodsReceipt.getItems()) {
                BigDecimal remaining = receivable.getOrDefault(item.getProductId(), BigDecimal.ZERO);
                if (nz(item.getQuantity()).compareTo(remaining) > 0) {
                    throw conflict("Cannot confirm GRN: quantity exceeds remaining receivable for a product");
                }
            }
        }
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
            throw missing("GRN");
        return goodsReceipt;
    }

    public PageResponse<GoodsReceipt> list(Pageable pageable) {
        return list(pageable, null);
    }

    public PageResponse<GoodsReceipt> list(Pageable pageable, UUID purchaseOrderId) {
        UUID org = TenantContext.getOrganizationId();
        String where = "g.organizationId=:org" + (purchaseOrderId != null ? " and g.purchaseOrderId=:po" : "");
        var countQ = em.createQuery("select count(g) from GoodsReceipt g where " + where, Long.class)
                .setParameter("org", org);
        var listQ = em.createQuery(
                        "from GoodsReceipt g where " + where + " order by g.createdAt desc", GoodsReceipt.class)
                .setParameter("org", org);
        if (purchaseOrderId != null) {
            countQ.setParameter("po", purchaseOrderId);
            listQ.setParameter("po", purchaseOrderId);
        }
        long total = countQ.getSingleResult();
        List<GoodsReceipt> content = listQ.setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize())
                .getResultList();
        return PageResponse.of(content, pageable, total);
    }

    public List<GoodsReceipt> listByPurchaseOrder(UUID poId) {
        return em.createQuery(
                        "from GoodsReceipt g where g.organizationId=:org and g.purchaseOrderId=:po order by g.createdAt desc",
                        GoodsReceipt.class)
                .setParameter("org", TenantContext.getOrganizationId())
                .setParameter("po", poId)
                .getResultList();
    }

    /** Remaining receivable qty per product (ordered − confirmed GRN qty), optionally excluding one draft GRN. */
    public Map<UUID, BigDecimal> receivableByProduct(UUID poId, UUID excludeGrnId) {
        PurchaseOrder po = orders.get(poId);
        Map<UUID, BigDecimal> ordered = new HashMap<>();
        for (PurchaseOrderItem item : po.getItems()) {
            ordered.merge(item.getProductId(), nz(item.getQuantity()), BigDecimal::add);
        }
        Map<UUID, BigDecimal> received = confirmedReceivedByProduct(poId, excludeGrnId);
        Map<UUID, BigDecimal> receivable = new HashMap<>();
        for (Map.Entry<UUID, BigDecimal> e : ordered.entrySet()) {
            BigDecimal rem = e.getValue().subtract(received.getOrDefault(e.getKey(), BigDecimal.ZERO));
            if (rem.signum() < 0) rem = BigDecimal.ZERO;
            receivable.put(e.getKey(), rem);
        }
        return receivable;
    }

    public Map<UUID, BigDecimal> receivedByProduct(UUID poId, UUID excludeGrnId) {
        return confirmedReceivedByProduct(poId, excludeGrnId);
    }

    public Map<UUID, BigDecimal> confirmedReceivedByProduct(UUID poId, UUID excludeGrnId) {
        List<GoodsReceipt> grns = listByPurchaseOrder(poId);
        Map<UUID, BigDecimal> received = new HashMap<>();
        for (GoodsReceipt grn : grns) {
            if (excludeGrnId != null && excludeGrnId.equals(grn.getId())) continue;
            if (!"CONFIRMED".equals(grn.getStatus())) continue;
            for (GoodsReceiptItem item : grn.getItems()) {
                received.merge(item.getProductId(), nz(item.getQuantity()), BigDecimal::add);
            }
        }
        return received;
    }

    private List<Line> resolveGrnLines(PurchaseOrder po, List<Line> requested, Map<UUID, BigDecimal> receivable) {
        List<Line> source;
        if (requested == null || requested.isEmpty()) {
            source = new ArrayList<>();
            for (PurchaseOrderItem item : po.getItems()) {
                BigDecimal qty = receivable.getOrDefault(item.getProductId(), BigDecimal.ZERO);
                if (qty.signum() <= 0) continue;
                source.add(new Line(
                        item.getProductId(),
                        item.getUnitId(),
                        item.getDescription(),
                        null,
                        qty,
                        item.getRate(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null));
            }
        } else {
            source = requested;
        }
        if (source.isEmpty()) throw badRequest("No remaining quantity to receive for this purchase order");
        for (Line line : source) {
            BigDecimal rem = receivable.getOrDefault(line.productId(), BigDecimal.ZERO);
            if (nz(line.quantity()).compareTo(rem) > 0) {
                throw conflict("Quantity exceeds remaining receivable for product " + line.productId());
            }
        }
        return source;
    }

    private void applyLines(GoodsReceipt goodsReceipt, List<Line> source) {
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
    }

    private PurchaseOrder requireConfirmedPo(UUID poId) {
        PurchaseOrder po = orders.get(poId);
        if (!"CONFIRMED".equals(po.getStatus())) {
            throw conflict("Purchase order must be confirmed before creating a GRN");
        }
        return po;
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

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private ResponseStatusException missing(String label) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, label + " not found");
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
