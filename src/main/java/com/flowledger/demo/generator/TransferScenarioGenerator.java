package com.flowledger.demo.generator;

import com.flowledger.common.tenant.TenantContext;
import com.flowledger.demo.DemoSeedContext;
import com.flowledger.demo.scenario.DemoBlueprint;
import com.flowledger.demo.util.DemoIsolatedWork;
import com.flowledger.demo.util.ProgressLogger;
import com.flowledger.inventory.dto.InventoryDtos.Transfer;
import com.flowledger.inventory.service.InventoryService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class TransferScenarioGenerator {
    private static final Logger log = LoggerFactory.getLogger(TransferScenarioGenerator.class);
    private static final int TRANSFER_COUNT = 5;

    private final InventoryService inventoryService;
    private final DemoIsolatedWork isolated;

    public TransferScenarioGenerator(InventoryService inventoryService, DemoIsolatedWork isolated) {
        this.inventoryService = inventoryService;
        this.isolated = isolated;
    }

    public void generate(DemoSeedContext ctx, ProgressLogger progress) {
        DemoBlueprint blueprint = ctx.getBlueprint();
        if (!blueprint.workflow().interStoreTransfers()) {
            progress.done("Inter-store transfers skipped");
            return;
        }

        TenantContext.set(ctx.getOrganizationId(), ctx.getAdminUserId());
        progress.stage("Inter-warehouse transfer vignettes");

        List<UUID> warehouses = ctx.getWarehouseIds();
        List<UUID> products = ctx.getProductIds();
        if (warehouses.size() < 2 || products.isEmpty()) {
            progress.done("Transfers skipped (need 2+ warehouses and products)");
            return;
        }

        List<String> vignettes = new ArrayList<>();
        for (int i = 0; i < TRANSFER_COUNT; i++) {
            UUID from = warehouses.get(i % warehouses.size());
            UUID to = warehouses.get((i + 1) % warehouses.size());
            if (from.equals(to)) {
                continue;
            }
            UUID productId = products.get(ThreadLocalRandom.current().nextInt(products.size()));
            String name = "Transfer " + (i + 1) + ": WH→WH restock";
            try {
                isolated.run(() -> inventoryService.transferStock(new Transfer(
                        productId,
                        from,
                        to,
                        BigDecimal.valueOf(1 + ThreadLocalRandom.current().nextInt(10)),
                        "Demo " + name)));
                vignettes.add(name);
            } catch (RuntimeException ex) {
                log.debug("Transfer vignette failed: {}", ex.getMessage());
                vignettes.add(name + " (failed)");
            }
        }

        ctx.getMeta().put("transferVignettes", vignettes);
        progress.done("Transfer scenarios (" + vignettes.size() + " vignettes)");
    }
}
