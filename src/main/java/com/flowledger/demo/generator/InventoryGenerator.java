package com.flowledger.demo.generator;

import com.flowledger.common.tenant.TenantContext;
import com.flowledger.demo.DemoSeedContext;
import com.flowledger.demo.scenario.DemoBlueprint;
import com.flowledger.demo.util.ProgressLogger;
import com.flowledger.inventory.dto.InventoryDtos.Adjustment;
import com.flowledger.inventory.dto.InventoryDtos.PostTransaction;
import com.flowledger.inventory.entity.InventoryTransaction.Type;
import com.flowledger.inventory.service.InventoryService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class InventoryGenerator {
    private final InventoryService inventoryService;

    public InventoryGenerator(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @Transactional
    public void generate(DemoSeedContext ctx, ProgressLogger progress) {
        DemoBlueprint blueprint = ctx.getBlueprint();
        TenantContext.set(ctx.getOrganizationId(), ctx.getAdminUserId());
        progress.stage("Posting opening stock");

        List<UUID> products = selectProducts(ctx, blueprint.productCount());
        List<UUID> warehouses = ctx.getWarehouseIds();
        if (products.isEmpty() || warehouses.isEmpty()) {
            progress.done("Opening stock skipped (no products/warehouses)");
            return;
        }

        boolean batchHeavy = blueprint.workflow().batchExpiryHeavy();
        boolean serialHeavy = blueprint.workflow().serialTrackingHeavy();
        int batchPostings = 0;
        int serialPostings = 0;

        int total = products.size() * Math.min(3, warehouses.size());
        int done = 0;
        for (UUID productId : products) {
            boolean zeroStock = ThreadLocalRandom.current().nextInt(100) < 10;
            int whSample = Math.min(3, warehouses.size());
            for (int w = 0; w < whSample; w++) {
                UUID warehouseId = warehouses.get(Math.floorMod(productId.hashCode() + w, warehouses.size()));
                BigDecimal qty = zeroStock
                        ? BigDecimal.ZERO
                        : BigDecimal.valueOf(ThreadLocalRandom.current().nextInt(101));
                if (qty.signum() == 0) {
                    continue;
                }
                try {
                    if (batchHeavy && ThreadLocalRandom.current().nextInt(100) < 70) {
                        LocalDate expiry = LocalDate.now()
                                .plusDays(ThreadLocalRandom.current().nextBoolean()
                                        ? ThreadLocalRandom.current().nextInt(5, 30)
                                        : ThreadLocalRandom.current().nextInt(60, 400));
                        String batch = "LOT-" + productId.toString().substring(0, 8).toUpperCase() + "-" + (w + 1);
                        inventoryService.postTransaction(new PostTransaction(
                                Type.OPENING_STOCK,
                                productId,
                                warehouseId,
                                qty,
                                BigDecimal.ZERO,
                                "OPENING_STOCK",
                                null,
                                null,
                                UUID.randomUUID().toString(),
                                batch,
                                null,
                                expiry,
                                null,
                                "Demo opening batch stock",
                                LocalDate.now()));
                        batchPostings++;
                    } else if (serialHeavy && qty.compareTo(BigDecimal.TEN) <= 0) {
                        int serialCount = Math.min(qty.intValue(), 5);
                        for (int s = 0; s < serialCount; s++) {
                            String serial = "SN-"
                                    + productId.toString().substring(0, 8).toUpperCase()
                                    + "-"
                                    + String.format("%03d", s + 1);
                            inventoryService.postTransaction(new PostTransaction(
                                    Type.OPENING_STOCK,
                                    productId,
                                    warehouseId,
                                    BigDecimal.ONE,
                                    BigDecimal.ZERO,
                                    "OPENING_STOCK",
                                    null,
                                    null,
                                    UUID.randomUUID().toString(),
                                    null,
                                    serial,
                                    null,
                                    null,
                                    "Demo serialized opening stock",
                                    LocalDate.now()));
                            serialPostings++;
                        }
                    } else {
                        inventoryService.openingStock(new Adjustment(productId, warehouseId, qty, "Demo opening stock"));
                    }
                } catch (Exception ex) {
                    // continue seeding other lines
                }
                done++;
                if (done % Math.max(1, total / 10) == 0 || done == total) {
                    progress.progress("Opening stock", done, total);
                }
            }
        }

        ctx.getMeta().put("batchOpeningPostings", batchPostings);
        ctx.getMeta().put("serialOpeningPostings", serialPostings);
        progress.done("Opening stock (" + done + " postings, batches=" + batchPostings + ", serials=" + serialPostings
                + ")");
    }

    private static List<UUID> selectProducts(DemoSeedContext ctx, int productCount) {
        List<UUID> all = new ArrayList<>(ctx.getProductIds());
        if (all.isEmpty()) {
            return all;
        }
        if (productCount > 5000) {
            Collections.shuffle(all, ThreadLocalRandom.current());
            int sample = (int) Math.ceil(all.size() * 0.30);
            return all.subList(0, Math.max(1, sample));
        }
        if (productCount > 2000) {
            Collections.shuffle(all, ThreadLocalRandom.current());
            return all.subList(0, Math.min(2000, all.size()));
        }
        return all;
    }
}
