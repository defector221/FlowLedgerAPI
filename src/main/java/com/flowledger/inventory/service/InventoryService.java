package com.flowledger.inventory.service;

import com.flowledger.common.tenant.TenantContext;
import com.flowledger.inventory.dto.InventoryDtos.*;
import com.flowledger.inventory.entity.*;
import com.flowledger.inventory.entity.InventoryTransaction.Type;
import com.flowledger.inventory.repository.*;
import com.flowledger.product.entity.Product;
import com.flowledger.product.repository.ProductRepository;
import com.flowledger.sales.repository.SalesInvoiceRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InventoryService {
    private final InventoryTransactionRepository txns;
    private final InventoryBatchRepository batches;
    private final SerialNumberRepository serials;
    private final ProductRepository products;
    private final SalesInvoiceRepository salesInvoices;

    public InventoryService(
            InventoryTransactionRepository transactionRepository,
            InventoryBatchRepository batchRepository,
            SerialNumberRepository serialRepository,
            ProductRepository productRepository,
            SalesInvoiceRepository salesInvoices) {
        txns = transactionRepository;
        batches = batchRepository;
        serials = serialRepository;
        products = productRepository;
        this.salesInvoices = salesInvoices;
    }

    @Transactional
    public InventoryTransaction postTransaction(PostTransaction request) {
        UUID org = TenantContext.getOrganizationId();
        if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
            var existing = txns.findByOrganizationIdAndIdempotencyKey(org, request.idempotencyKey());
            if (existing.isPresent()) return existing.get();
        }
        BigDecimal in = n(request.inward()), out = n(request.outward());
        if (in.signum() < 0
                || out.signum() < 0
                || (in.signum() > 0 && out.signum() > 0)
                || in.signum() == 0 && out.signum() == 0)
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Specify exactly one positive inward or outward quantity");
        InventoryTransaction transaction = new InventoryTransaction();
        transaction.setOrganizationId(org);
        transaction.setTransactionType(request.type());
        transaction.setProductId(request.productId());
        transaction.setWarehouseId(request.warehouseId());
        transaction.setTransactionDate(request.transactionDate() == null ? LocalDate.now() : request.transactionDate());
        transaction.setInwardQty(in);
        transaction.setOutwardQty(out);
        transaction.setReferenceType(request.referenceType());
        transaction.setReferenceId(request.referenceId());
        transaction.setReferenceNumber(request.referenceNumber());
        transaction.setIdempotencyKey(blank(request.idempotencyKey()));
        transaction.setBatchNumber(blank(request.batchNumber()));
        transaction.setSerialNumber(blank(request.serialNumber()));
        transaction.setExpiryDate(request.expiryDate());
        transaction.setUnitCost(request.unitCost());
        transaction.setNotes(request.notes());
        transaction = txns.save(transaction);
        updateBatchAndSerial(transaction);
        return transaction;
    }

    @Transactional(readOnly = true)
    public Stock getStock(UUID product, UUID warehouse) {
        return new Stock(product, warehouse, txns.stockBalance(TenantContext.getOrganizationId(), product, warehouse));
    }

    @Transactional(readOnly = true)
    public List<StockPosition> stockOverview() {
        UUID org = TenantContext.getOrganizationId();
        Map<UUID, BigDecimal> draftReserved = new HashMap<>();
        for (Object[] row : salesInvoices.sumDraftQuantitiesByProduct(org)) {
            UUID productId = toUuid(row[0]);
            BigDecimal qty = row[1] instanceof BigDecimal bd ? bd : new BigDecimal(row[1].toString());
            draftReserved.put(productId, qty);
        }
        return products.findAll().stream()
                .filter(product ->
                        org.equals(product.getOrganizationId()) && product.isActive() && isStockedProduct(product))
                .sorted(Comparator.comparing(Product::getName, String.CASE_INSENSITIVE_ORDER))
                .map(product -> new StockPosition(
                        product.getId(),
                        product.getName(),
                        product.getSku(),
                        txns.stockBalance(org, product.getId(), null),
                        draftReserved.getOrDefault(product.getId(), BigDecimal.ZERO),
                        n(product.getMinimumStockLevel()),
                        n(product.getReorderLevel())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Ledger> getStockLedger(UUID product, UUID warehouse, LocalDate from, LocalDate to) {
        UUID org = TenantContext.getOrganizationId();
        LocalDate start = from == null ? LocalDate.of(1970, 1, 1) : from, end = to == null ? LocalDate.now() : to;
        List<InventoryTransaction> rows = warehouse == null
                ? txns.findByOrganizationIdAndProductIdAndTransactionDateBetweenOrderByTransactionDateAscCreatedAtAsc(
                        org, product, start, end)
                : txns
                        .findByOrganizationIdAndProductIdAndWarehouseIdAndTransactionDateBetweenOrderByTransactionDateAscCreatedAtAsc(
                                org, product, warehouse, start, end);
        BigDecimal running = BigDecimal.ZERO;
        List<Ledger> result = new ArrayList<>();
        for (var transaction : rows) {
            running = running.add(transaction.getInwardQty()).subtract(transaction.getOutwardQty());
            result.add(new Ledger(
                    transaction.getTransactionDate(),
                    transaction.getTransactionType(),
                    transaction.getReferenceNumber(),
                    transaction.getInwardQty(),
                    transaction.getOutwardQty(),
                    running));
        }
        return result;
    }

    @Transactional
    public InventoryTransaction adjustStock(Adjustment request) {
        BigDecimal quantity = request.quantity();
        if (quantity.signum() == 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Adjustment cannot be zero");
        return postTransaction(new PostTransaction(
                Type.STOCK_ADJUSTMENT,
                request.productId(),
                request.warehouseId(),
                quantity.signum() > 0 ? quantity : BigDecimal.ZERO,
                quantity.signum() < 0 ? quantity.abs() : BigDecimal.ZERO,
                "ADJUSTMENT",
                null,
                null,
                UUID.randomUUID().toString(),
                null,
                null,
                null,
                null,
                request.notes(),
                LocalDate.now()));
    }

    @Transactional
    public void transferStock(Transfer request) {
        if (request.fromWarehouseId().equals(request.toWarehouseId()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Warehouses must differ");
        String key = UUID.randomUUID().toString();
        postTransaction(new PostTransaction(
                Type.STOCK_TRANSFER,
                request.productId(),
                request.fromWarehouseId(),
                BigDecimal.ZERO,
                request.quantity(),
                "STOCK_TRANSFER",
                null,
                null,
                key + "-OUT",
                null,
                null,
                null,
                null,
                request.notes(),
                LocalDate.now()));
        postTransaction(new PostTransaction(
                Type.STOCK_TRANSFER,
                request.productId(),
                request.toWarehouseId(),
                request.quantity(),
                BigDecimal.ZERO,
                "STOCK_TRANSFER",
                null,
                null,
                key + "-IN",
                null,
                null,
                null,
                null,
                request.notes(),
                LocalDate.now()));
    }

    @Transactional
    public InventoryTransaction openingStock(Adjustment request) {
        return postTransaction(new PostTransaction(
                Type.OPENING_STOCK,
                request.productId(),
                request.warehouseId(),
                request.quantity(),
                BigDecimal.ZERO,
                "OPENING_STOCK",
                null,
                null,
                UUID.randomUUID().toString(),
                null,
                null,
                null,
                null,
                request.notes(),
                LocalDate.now()));
    }

    @Transactional(readOnly = true)
    public List<Alert> lowStockAlerts(boolean reorder) {
        UUID org = TenantContext.getOrganizationId();
        return products.findAll().stream()
                .filter(product ->
                        org.equals(product.getOrganizationId()) && product.isActive() && isStockedProduct(product))
                .map(product -> new Alert(
                        product.getId(),
                        product.getName(),
                        txns.stockBalance(org, product.getId(), null),
                        reorder ? n(product.getReorderLevel()) : n(product.getMinimumStockLevel())))
                .filter(alert ->
                        alert.threshold().signum() > 0 && alert.available().compareTo(alert.threshold()) <= 0)
                .toList();
    }

    @Transactional
    public boolean postPurchase(
            UUID warehouseId,
            UUID productId,
            BigDecimal quantity,
            BigDecimal unitCost,
            LocalDate date,
            UUID referenceId,
            String referenceNumber,
            String idempotencyKey) {
        InventoryTransaction existing = postTransaction(new PostTransaction(
                Type.PURCHASE,
                productId,
                warehouseId,
                quantity,
                BigDecimal.ZERO,
                "GOODS_RECEIPT",
                referenceId,
                referenceNumber,
                idempotencyKey,
                null,
                null,
                null,
                unitCost,
                null,
                date));
        return existing != null;
    }

    @Transactional
    public boolean postPurchaseReturn(
            UUID warehouseId,
            UUID productId,
            BigDecimal quantity,
            BigDecimal unitCost,
            LocalDate date,
            UUID referenceId,
            String referenceNumber,
            String idempotencyKey) {
        postTransaction(new PostTransaction(
                Type.PURCHASE_RETURN,
                productId,
                warehouseId,
                BigDecimal.ZERO,
                quantity,
                "PURCHASE_RETURN",
                referenceId,
                referenceNumber,
                idempotencyKey,
                null,
                null,
                null,
                unitCost,
                null,
                date));
        return true;
    }

    private void updateBatchAndSerial(InventoryTransaction transaction) {
        BigDecimal delta = transaction.getInwardQty().subtract(transaction.getOutwardQty());
        if (transaction.getBatchNumber() != null) {
            InventoryBatch batch = batches.findByOrganizationIdAndProductIdAndWarehouseIdAndBatchNumber(
                            transaction.getOrganizationId(),
                            transaction.getProductId(),
                            transaction.getWarehouseId(),
                            transaction.getBatchNumber())
                    .orElseGet(InventoryBatch::new);
            if (batch.getId() == null) {
                batch.setOrganizationId(transaction.getOrganizationId());
                batch.setProductId(transaction.getProductId());
                batch.setWarehouseId(transaction.getWarehouseId());
                batch.setBatchNumber(transaction.getBatchNumber());
                batch.setExpiryDate(transaction.getExpiryDate());
                batch.setQuantity(BigDecimal.ZERO);
            }
            batch.setQuantity(n(batch.getQuantity()).add(delta));
            batches.save(batch);
        }
        if (transaction.getSerialNumber() != null) {
            SerialNumber serial = serials.findByOrganizationIdAndProductIdAndSerialNumber(
                            transaction.getOrganizationId(), transaction.getProductId(), transaction.getSerialNumber())
                    .orElseGet(SerialNumber::new);
            if (serial.getId() == null) {
                serial.setOrganizationId(transaction.getOrganizationId());
                serial.setProductId(transaction.getProductId());
                serial.setSerialNumber(transaction.getSerialNumber());
            }
            serial.setWarehouseId(transaction.getWarehouseId());
            serial.setStatus(delta.signum() >= 0 ? SerialNumber.Status.IN_STOCK : SerialNumber.Status.SOLD);
            serials.save(serial);
        }
    }

    private static boolean isStockedProduct(Product product) {
        return product.getItemType() == null
                || product.getItemType().isBlank()
                || "PRODUCT".equalsIgnoreCase(product.getItemType());
    }

    private static UUID toUuid(Object value) {
        if (value instanceof UUID uuid) return uuid;
        return UUID.fromString(value.toString());
    }

    private static BigDecimal n(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String blank(String v) {
        return v == null || v.isBlank() ? null : v;
    }
}
