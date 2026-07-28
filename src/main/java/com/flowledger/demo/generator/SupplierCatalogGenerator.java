package com.flowledger.demo.generator;

import com.flowledger.common.tenant.TenantContext;
import com.flowledger.demo.DemoSeedContext;
import com.flowledger.demo.util.DemoIsolatedWork;
import com.flowledger.demo.util.ProgressLogger;
import com.flowledger.product.dto.SupplierCatalogDtos.Create;
import com.flowledger.product.entity.Product;
import com.flowledger.product.repository.ProductRepository;
import com.flowledger.product.service.SupplierCatalogService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Links demo products to suppliers so PO / supplier pricing UI is usable out of the box.
 * Runs after catalog + parties (suppliers must already exist).
 */
@Component
public class SupplierCatalogGenerator {
    private static final Logger log = LoggerFactory.getLogger(SupplierCatalogGenerator.class);

    private final SupplierCatalogService supplierCatalog;
    private final ProductRepository products;
    private final DemoIsolatedWork isolated;

    public SupplierCatalogGenerator(
            SupplierCatalogService supplierCatalog, ProductRepository products, DemoIsolatedWork isolated) {
        this.supplierCatalog = supplierCatalog;
        this.products = products;
        this.isolated = isolated;
    }

    public void generate(DemoSeedContext ctx, ProgressLogger progress) {
        TenantContext.set(ctx.getOrganizationId(), ctx.getAdminUserId());
        progress.stage("Linking products to suppliers");

        List<UUID> productIds = ctx.getProductIds();
        List<UUID> supplierIds = ctx.getSupplierIds();
        if (productIds.isEmpty() || supplierIds.isEmpty()) {
            progress.done("Supplier catalog skipped (no products/suppliers)");
            return;
        }

        int linked = 0;
        int failures = 0;
        int total = productIds.size();

        for (int i = 0; i < productIds.size(); i++) {
            UUID productId = productIds.get(i);
            UUID primarySupplier = supplierIds.get(i % supplierIds.size());
            UUID secondarySupplier = supplierIds.size() > 1
                    ? supplierIds.get((i + 1) % supplierIds.size())
                    : null;

            Product product = products
                    .findByIdAndOrganizationId(productId, ctx.getOrganizationId())
                    .orElse(null);
            if (product == null) {
                failures++;
                continue;
            }

            BigDecimal purchase = product.getPurchasePrice() != null && product.getPurchasePrice().signum() > 0
                    ? product.getPurchasePrice()
                    : new BigDecimal("100.00");
            String sku = product.getSku() != null ? product.getSku() : "SKU-" + (i + 1);

            if (link(ctx, productId, primarySupplier, sku, purchase, true)) {
                linked++;
            } else {
                failures++;
            }

            // ~40% of products get a second supplier option
            if (secondarySupplier != null
                    && !secondarySupplier.equals(primarySupplier)
                    && ThreadLocalRandom.current().nextInt(100) < 40) {
                BigDecimal alt = purchase
                        .multiply(BigDecimal.valueOf(0.95 + ThreadLocalRandom.current().nextDouble() * 0.15))
                        .setScale(2, RoundingMode.HALF_UP);
                if (link(ctx, productId, secondarySupplier, sku + "-ALT", alt, false)) {
                    linked++;
                }
            }

            if ((i + 1) % Math.max(1, total / 10) == 0 || i + 1 == total) {
                progress.progress("Supplier links", i + 1, total);
            }
            TenantContext.set(ctx.getOrganizationId(), ctx.getAdminUserId());
        }

        ctx.getMeta().put("supplierCatalogLinks", linked);
        ctx.getMeta().put("supplierCatalogFailures", failures);
        progress.done("Supplier catalog (" + linked + " links, " + failures + " failures)");
    }

    private boolean link(
            DemoSeedContext ctx,
            UUID productId,
            UUID supplierId,
            String supplierSku,
            BigDecimal purchasePrice,
            boolean preferred) {
        try {
            isolated.run(() -> {
                TenantContext.set(ctx.getOrganizationId(), ctx.getAdminUserId());
                supplierCatalog.createForProduct(
                        productId,
                        new Create(
                                productId,
                                supplierId,
                                supplierSku,
                                null,
                                null,
                                purchasePrice,
                                "INR",
                                BigDecimal.ONE,
                                3 + ThreadLocalRandom.current().nextInt(12),
                                preferred,
                                null,
                                null,
                                "Demo supplier price",
                                true));
            });
            return true;
        } catch (RuntimeException ex) {
            log.debug("Supplier link failed product={} supplier={}: {}", productId, supplierId, ex.getMessage());
            return false;
        }
    }
}
