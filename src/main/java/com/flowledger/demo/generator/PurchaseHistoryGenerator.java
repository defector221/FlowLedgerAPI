package com.flowledger.demo.generator;

import com.flowledger.common.tenant.TenantContext;
import com.flowledger.demo.DemoSeedContext;
import com.flowledger.demo.scenario.DemoBlueprint;
import com.flowledger.demo.util.DemoIsolatedWork;
import com.flowledger.demo.util.ProgressLogger;
import com.flowledger.product.repository.ProductRepository;
import com.flowledger.purchase.dto.PurchaseDtos.InvoiceRequest;
import com.flowledger.purchase.dto.PurchaseDtos.Line;
import com.flowledger.purchase.service.PurchaseInvoiceService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PurchaseHistoryGenerator {
    private static final Logger log = LoggerFactory.getLogger(PurchaseHistoryGenerator.class);

    private final PurchaseInvoiceService purchaseInvoiceService;
    private final ProductRepository products;
    private final DemoIsolatedWork isolated;

    public PurchaseHistoryGenerator(
            PurchaseInvoiceService purchaseInvoiceService, ProductRepository products, DemoIsolatedWork isolated) {
        this.purchaseInvoiceService = purchaseInvoiceService;
        this.products = products;
        this.isolated = isolated;
    }

    /** Each invoice runs in REQUIRES_NEW — do not wrap this method in @Transactional. */
    public void generate(DemoSeedContext ctx, ProgressLogger progress) {
        DemoBlueprint blueprint = ctx.getBlueprint();
        TenantContext.set(ctx.getOrganizationId(), ctx.getAdminUserId());
        progress.stage("Creating purchase history");

        if (ctx.getSupplierIds().isEmpty()
                || ctx.getProductIds().isEmpty()
                || ctx.getWarehouseIds().isEmpty()) {
            progress.done("Purchase history skipped");
            return;
        }

        int target = blueprint.purchaseCount();
        int created = 0;
        int failures = 0;
        for (int i = 1; i <= target; i++) {
            final int index = i;
            try {
                isolated.run(() -> {
                    TenantContext.set(ctx.getOrganizationId(), ctx.getAdminUserId());
                    UUID supplierId = ctx.getSupplierIds()
                            .get(ThreadLocalRandom.current()
                                    .nextInt(ctx.getSupplierIds().size()));
                    UUID warehouseId = ctx.getWarehouseIds()
                            .get(ThreadLocalRandom.current()
                                    .nextInt(ctx.getWarehouseIds().size()));
                    LocalDate invoiceDate = LocalDate.now()
                            .minusDays(ThreadLocalRandom.current().nextInt(365));
                    int lineCount = 1 + ThreadLocalRandom.current().nextInt(5);
                    List<Line> lines =
                            buildLines(ctx, lineCount, blueprint.workflow().largePurchaseOrders());

                    var invoice = purchaseInvoiceService.createStandalone(
                            supplierId,
                            warehouseId,
                            new InvoiceRequest(
                                    "SUP-INV-" + String.format("%06d", index),
                                    invoiceDate,
                                    invoiceDate.plusDays(30),
                                    "Karnataka",
                                    false,
                                    "Demo purchase",
                                    lines));
                    purchaseInvoiceService.confirm(invoice.getId());
                });
                created++;
            } catch (RuntimeException ex) {
                failures++;
                log.warn(
                        "[{}] Purchase invoice {} failed: {}", ctx.getScenario().slug(), i, ex.getMessage());
            }
            TenantContext.set(ctx.getOrganizationId(), ctx.getAdminUserId());
            if (i % Math.max(1, target / 10) == 0 || i == target) {
                progress.progress("Purchases", i, target);
            }
        }

        ctx.getMeta().put("purchaseInvoicesCreated", created);
        ctx.getMeta().put("purchaseInvoiceFailures", failures);
        progress.done("Purchase history (" + created + " invoices, " + failures + " failures)");
    }

    private List<Line> buildLines(DemoSeedContext ctx, int lineCount, boolean largeOrders) {
        List<Line> lines = new ArrayList<>();
        for (int l = 0; l < lineCount; l++) {
            UUID productId = ctx.getProductIds()
                    .get(ThreadLocalRandom.current().nextInt(ctx.getProductIds().size()));
            BigDecimal rate = products.findByIdAndOrganizationId(productId, ctx.getOrganizationId())
                    .map(p -> p.getPurchasePrice() != null ? p.getPurchasePrice() : p.getSellingPrice())
                    .orElse(new BigDecimal("100"));
            if (rate == null || rate.signum() <= 0) {
                rate = new BigDecimal("100");
            }
            BigDecimal qty = largeOrders
                    ? BigDecimal.valueOf(100 + ThreadLocalRandom.current().nextInt(4900))
                    : BigDecimal.valueOf(1 + ThreadLocalRandom.current().nextInt(20));
            lines.add(new Line(
                    productId,
                    null,
                    "Demo line",
                    null,
                    qty,
                    rate,
                    null,
                    new BigDecimal("18"),
                    "GST",
                    null,
                    null,
                    null));
        }
        return lines;
    }
}
