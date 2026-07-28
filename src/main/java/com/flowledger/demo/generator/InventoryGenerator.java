package com.flowledger.demo.generator;

import com.flowledger.common.tenant.TenantContext;
import com.flowledger.demo.DemoSeedContext;
import com.flowledger.demo.scenario.DemoBlueprint;
import com.flowledger.demo.util.DemoIsolatedWork;
import com.flowledger.demo.util.ProgressLogger;
import com.flowledger.inventory.dto.InventoryDtos.Adjustment;
import com.flowledger.inventory.service.InventoryService;
import com.flowledger.organization.repository.OrganizationSettingsRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class InventoryGenerator {
    private static final Logger log = LoggerFactory.getLogger(InventoryGenerator.class);

    private final InventoryService inventoryService;
    private final OrganizationSettingsRepository settings;
    private final DemoIsolatedWork isolated;

    public InventoryGenerator(
            InventoryService inventoryService,
            OrganizationSettingsRepository settings,
            DemoIsolatedWork isolated) {
        this.inventoryService = inventoryService;
        this.settings = settings;
        this.isolated = isolated;
    }

    /** Each posting runs in REQUIRES_NEW — do not wrap this method in @Transactional. */
    public void generate(DemoSeedContext ctx, ProgressLogger progress) {
        DemoBlueprint blueprint = ctx.getBlueprint();
        TenantContext.set(ctx.getOrganizationId(), ctx.getAdminUserId());
        progress.stage("Posting opening stock");

        List<UUID> products = selectProducts(ctx, blueprint.productCount());
        List<UUID> targetWarehouses = storeFirstWarehouses(ctx);
        if (products.isEmpty() || targetWarehouses.isEmpty()) {
            progress.done("Opening stock skipped (no products/warehouses)");
            return;
        }

        UUID defaultWh = targetWarehouses.get(0);
        settings.findByOrganizationId(ctx.getOrganizationId()).ifPresent(s -> {
            s.setDefaultWarehouseId(defaultWh);
            settings.save(s);
        });

        int failures = 0;
        int posted = 0;
        int total = products.size() * targetWarehouses.size();
        int done = 0;

        for (UUID productId : products) {
            for (UUID warehouseId : targetWarehouses) {
                // Solid POS-ready stock on every store warehouse (not random / not zero).
                BigDecimal qty = BigDecimal.valueOf(100 + ThreadLocalRandom.current().nextInt(101));
                boolean ok = tryPost(ctx, () -> inventoryService.openingStock(
                        new Adjustment(productId, warehouseId, qty, "Demo opening stock")));
                if (ok) {
                    posted++;
                } else {
                    failures++;
                }
                done++;
                if (done % Math.max(1, total / 10) == 0 || done == total) {
                    progress.progress("Opening stock", done, total);
                }
            }
        }

        ctx.getMeta().put("openingStockPosted", posted);
        ctx.getMeta().put("openingStockFailures", failures);
        ctx.getMeta().put("openingStockWarehouses", targetWarehouses.size());
        progress.done("Opening stock (posted=" + posted + ", warehouses=" + targetWarehouses.size()
                + ", failures=" + failures + ")");
    }

    private static List<UUID> storeFirstWarehouses(DemoSeedContext ctx) {
        Set<UUID> ids = new LinkedHashSet<>(ctx.getStoreWarehouseIds().values());
        if (ids.isEmpty()) {
            // Fallback: first warehouse per org (usually branch MAIN)
            if (!ctx.getWarehouseIds().isEmpty()) {
                ids.add(ctx.getWarehouseIds().get(0));
            }
        }
        return new ArrayList<>(ids);
    }

    private boolean tryPost(DemoSeedContext ctx, Runnable action) {
        try {
            isolated.run(() -> {
                TenantContext.set(ctx.getOrganizationId(), ctx.getAdminUserId());
                action.run();
            });
            return true;
        } catch (RuntimeException ex) {
            log.warn("Opening stock posting failed (isolated): {}", ex.getMessage());
            return false;
        }
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
