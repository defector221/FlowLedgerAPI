package com.flowledger.demo.generator;

import static com.flowledger.retail.dto.RetailDtos.CheckoutRequest;
import static com.flowledger.retail.dto.RetailDtos.OpenShiftRequest;
import static com.flowledger.retail.dto.RetailDtos.PaymentInput;
import static com.flowledger.retail.dto.RetailDtos.PosLineRequest;
import static com.flowledger.retail.dto.RetailDtos.PosSaleRequest;

import com.flowledger.common.tenant.TenantContext;
import com.flowledger.customer.repository.CustomerRepository;
import com.flowledger.demo.DemoSeedContext;
import com.flowledger.demo.scenario.DemoBlueprint;
import com.flowledger.demo.util.DemoIsolatedWork;
import com.flowledger.demo.util.ProgressLogger;
import com.flowledger.product.repository.ProductRepository;
import com.flowledger.retail.domain.RetailEnums.PaymentMode;
import com.flowledger.retail.dto.RetailDtos.CashierRequest;
import com.flowledger.retail.dto.RetailDtos.ShiftResponse;
import com.flowledger.retail.service.PosSaleService;
import com.flowledger.retail.service.RetailShiftService;
import com.flowledger.retail.service.RetailStoreService;
import com.flowledger.sales.dto.SalesDtos.Invoice;
import com.flowledger.sales.dto.SalesDtos.Item;
import com.flowledger.sales.service.SalesInvoiceService;
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
public class PosSalesHistoryGenerator {
    private static final Logger log = LoggerFactory.getLogger(PosSalesHistoryGenerator.class);
    private static final PaymentMode[] POS_MODES = {PaymentMode.CASH, PaymentMode.UPI, PaymentMode.CARD};
    private static final int CHUNK = 100;
    private static final int MAX_FAILURES = 50;

    private final PosSaleService posSaleService;
    private final RetailShiftService retailShiftService;
    private final RetailStoreService retailStoreService;
    private final SalesInvoiceService salesInvoiceService;
    private final ProductRepository products;
    private final CustomerRepository customers;
    private final DemoIsolatedWork isolated;

    public PosSalesHistoryGenerator(
            PosSaleService posSaleService,
            RetailShiftService retailShiftService,
            RetailStoreService retailStoreService,
            SalesInvoiceService salesInvoiceService,
            ProductRepository products,
            CustomerRepository customers,
            DemoIsolatedWork isolated) {
        this.posSaleService = posSaleService;
        this.retailShiftService = retailShiftService;
        this.retailStoreService = retailStoreService;
        this.salesInvoiceService = salesInvoiceService;
        this.products = products;
        this.customers = customers;
        this.isolated = isolated;
    }

    /** Each sale runs in its own REQUIRES_NEW TX — do not wrap this method in @Transactional. */
    public void generate(DemoSeedContext ctx, ProgressLogger progress) {
        DemoBlueprint blueprint = ctx.getBlueprint();
        bindTenant(ctx, null, null, null);
        progress.stage("Creating sales history");

        if (ctx.getStoreIds().isEmpty() || ctx.getProductIds().isEmpty()) {
            progress.done("Sales history skipped");
            return;
        }

        int target = blueprint.salesCount();
        int posTarget = blueprint.workflow().posHeavy() ? target : target / 4;
        int creditTarget = blueprint.workflow().creditSalesHeavy() ? Math.max(target - posTarget, target / 3) : 0;

        int posCreated = 0;
        int creditCreated = 0;
        int failures = 0;

        UUID storeId = ctx.getStoreIds().get(0);
        UUID branchId = ctx.getBranchIds().isEmpty() ? null : ctx.getBranchIds().get(0);
        UUID storeWarehouseId = ctx.getStoreWarehouseIds()
                .getOrDefault(
                        storeId,
                        ctx.getWarehouseIds().isEmpty()
                                ? null
                                : ctx.getWarehouseIds().get(0));
        bindTenant(ctx, branchId, storeId, storeWarehouseId);

        ShiftContext shift =
                blueprint.workflow().posHeavy() ? openShift(ctx, storeId, branchId, storeWarehouseId) : null;

        for (int i = 1; i <= posTarget; i++) {
            if (target > 1000 && i % CHUNK == 0 && failures > MAX_FAILURES) {
                log.warn(
                        "[{}] Stopping POS seed early after {} failures at {}",
                        ctx.getScenario().slug(),
                        failures,
                        i);
                break;
            }
            try {
                isolated.run(() -> {
                    bindTenant(ctx, branchId, storeId, storeWarehouseId);
                    UUID customerId = createPosSale(ctx, storeId, storeWarehouseId, shift);
                    recordPosSupplyType(ctx, customerId);
                });
                posCreated++;
            } catch (RuntimeException ex) {
                failures++;
                Throwable root = rootCause(ex);
                if (failures <= 5 || failures % 25 == 0) {
                    log.warn(
                            "[{}] POS sale {} failed: {}",
                            ctx.getScenario().slug(),
                            i,
                            root.getMessage() != null ? root.getMessage() : ex.getMessage());
                }
            }
            // AFTER_COMMIT listeners clear TenantContext — rebind for the next iteration.
            bindTenant(ctx, branchId, storeId, storeWarehouseId);
            if (i % Math.max(1, posTarget / 10) == 0 || i == posTarget) {
                progress.progress("POS sales", i, posTarget);
            }
        }

        for (int i = 1; i <= creditTarget; i++) {
            if (ctx.getCustomerIds().isEmpty()) {
                break;
            }
            try {
                final int invoiceIndex = i;
                isolated.run(() -> {
                    bindTenant(ctx, branchId, storeId, storeWarehouseId);
                    createCreditInvoice(ctx, storeWarehouseId, invoiceIndex);
                });
                creditCreated++;
            } catch (RuntimeException ex) {
                failures++;
                Throwable root = rootCause(ex);
                if (failures <= 5 || failures % 25 == 0) {
                    log.warn(
                            "[{}] Credit invoice {} failed: {}",
                            ctx.getScenario().slug(),
                            i,
                            root.getMessage() != null ? root.getMessage() : ex.getMessage());
                }
            }
            bindTenant(ctx, branchId, storeId, storeWarehouseId);
        }

        ctx.getMeta().put("posSalesCreated", posCreated);
        ctx.getMeta().put("creditInvoicesCreated", creditCreated);
        ctx.getMeta().put("salesSeedFailures", failures);
        progress.done(
                String.format("Sales history (%d POS, %d credit, %d failures)", posCreated, creditCreated, failures));
    }

    private ShiftContext openShift(DemoSeedContext ctx, UUID storeId, UUID branchId, UUID warehouseId) {
        try {
            return isolated.call(() -> {
                bindTenant(ctx, branchId, storeId, warehouseId);
                var counters = retailStoreService.listCounters(storeId);
                var terminals = retailStoreService.listTerminals(storeId);
                if (counters.isEmpty() || terminals.isEmpty()) {
                    return null;
                }
                UUID counterId = counters.get(0).id();
                UUID terminalId = terminals.get(0).id();
                UUID cashierUserId = ctx.getAdminUserId();
                UUID cashierId = retailStoreService.listCashiers(storeId).stream()
                        .findFirst()
                        .map(c -> c.id())
                        .orElseGet(() -> retailStoreService
                                .createCashier(
                                        new CashierRequest(storeId, cashierUserId, "EMP001", "Demo Cashier", "ACTIVE"))
                                .id());
                ShiftResponse shift = retailShiftService.open(new OpenShiftRequest(
                        storeId, counterId, terminalId, cashierId, new BigDecimal("5000"), "Demo shift"));
                return new ShiftContext(counterId, terminalId, cashierId, shift.id());
            });
        } catch (RuntimeException ex) {
            log.warn("Shift open skipped: {}", ex.getMessage());
            return null;
        } finally {
            bindTenant(ctx, branchId, storeId, warehouseId);
        }
    }

    private UUID createPosSale(DemoSeedContext ctx, UUID storeId, UUID storeWarehouseId, ShiftContext shift) {
        UUID counterId = shift != null ? shift.counterId() : null;
        UUID terminalId = shift != null ? shift.terminalId() : pickTerminal(ctx);
        UUID shiftId = shift != null ? shift.shiftId() : null;
        UUID cashierId = shift != null ? shift.cashierId() : null;
        UUID customerId = randomCustomer(ctx);

        var draft = posSaleService.createDraft(
                new PosSaleRequest(storeId, counterId, terminalId, shiftId, cashierId, customerId, null));

        int lines = 1 + ThreadLocalRandom.current().nextInt(4);
        for (int l = 0; l < lines; l++) {
            UUID productId = ctx.getProductIds()
                    .get(ThreadLocalRandom.current().nextInt(ctx.getProductIds().size()));
            BigDecimal rate = products.findByIdAndOrganizationId(productId, ctx.getOrganizationId())
                    .map(p -> p.getSellingPrice() != null ? p.getSellingPrice() : new BigDecimal("99"))
                    .orElse(new BigDecimal("99"));
            posSaleService.addLine(
                    draft.id(),
                    new PosLineRequest(
                            productId,
                            null,
                            null,
                            null,
                            BigDecimal.ONE,
                            rate,
                            null,
                            new BigDecimal("18"),
                            storeWarehouseId,
                            null,
                            null));
        }

        var refreshed = posSaleService.get(draft.id());
        PaymentMode mode = POS_MODES[ThreadLocalRandom.current().nextInt(POS_MODES.length)];
        posSaleService.checkout(
                draft.id(),
                new CheckoutRequest(
                        refreshed.customerId(),
                        "POS_RECEIPT",
                        List.of(new PaymentInput(mode, refreshed.grandTotal(), "DEMO-" + draft.id())),
                        null));
        return refreshed.customerId() != null ? refreshed.customerId() : customerId;
    }

    private void recordPosSupplyType(DemoSeedContext ctx, UUID customerId) {
        if (customerId == null) {
            ctx.setIntraStateInvoices(ctx.getIntraStateInvoices() + 1);
            return;
        }
        boolean interState = customers
                .findByIdAndOrganizationId(customerId, ctx.getOrganizationId())
                .map(customer -> customer.getStateCode() != null
                        && !"29".equals(customer.getStateCode().trim()))
                .orElse(false);
        if (interState) {
            ctx.setInterStateInvoices(ctx.getInterStateInvoices() + 1);
        } else {
            ctx.setIntraStateInvoices(ctx.getIntraStateInvoices() + 1);
        }
    }

    private void createCreditInvoice(DemoSeedContext ctx, UUID warehouseId, int index) {
        UUID customerId = ctx.getCustomerIds()
                .get(ThreadLocalRandom.current().nextInt(ctx.getCustomerIds().size()));
        List<Item> items = new ArrayList<>();
        int lines = 1 + ThreadLocalRandom.current().nextInt(3);
        for (int l = 0; l < lines; l++) {
            UUID productId = ctx.getProductIds()
                    .get(ThreadLocalRandom.current().nextInt(ctx.getProductIds().size()));
            BigDecimal rate = products.findByIdAndOrganizationId(productId, ctx.getOrganizationId())
                    .map(p -> p.getSellingPrice() != null ? p.getSellingPrice() : new BigDecimal("199"))
                    .orElse(new BigDecimal("199"));
            items.add(new Item(
                    productId,
                    "Credit sale line",
                    null,
                    BigDecimal.valueOf(1 + ThreadLocalRandom.current().nextInt(5)),
                    null,
                    rate,
                    null,
                    new BigDecimal("18"),
                    "GST",
                    null,
                    null,
                    null));
        }
        boolean interState = index % 3 == 0;
        String placeOfSupply = interState ? "Maharashtra" : "Karnataka";
        var draft = salesInvoiceService.createDraft(new Invoice(
                customerId,
                LocalDate.now().minusDays(ThreadLocalRandom.current().nextInt(180)),
                LocalDate.now().plusDays(30),
                warehouseId,
                null,
                null,
                null,
                null,
                placeOfSupply,
                false,
                null,
                null,
                null,
                "Demo credit sale",
                null,
                null,
                items));
        salesInvoiceService.confirm(draft.id());
        if (interState) {
            ctx.setInterStateInvoices(ctx.getInterStateInvoices() + 1);
        } else {
            ctx.setIntraStateInvoices(ctx.getIntraStateInvoices() + 1);
        }
    }

    private static void bindTenant(DemoSeedContext ctx, UUID branchId, UUID storeId, UUID warehouseId) {
        TenantContext.set(ctx.getOrganizationId(), ctx.getAdminUserId());
        if (branchId != null || storeId != null || warehouseId != null) {
            TenantContext.setLocation(branchId, storeId, warehouseId);
        }
    }

    private static Throwable rootCause(Throwable ex) {
        Throwable cur = ex;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur;
    }

    private static UUID randomCustomer(DemoSeedContext ctx) {
        if (ctx.getCustomerIds().isEmpty() || ThreadLocalRandom.current().nextInt(3) == 0) {
            return null;
        }
        return ctx.getCustomerIds()
                .get(ThreadLocalRandom.current().nextInt(ctx.getCustomerIds().size()));
    }

    private static UUID pickTerminal(DemoSeedContext ctx) {
        if (ctx.getTerminalIds().isEmpty()) {
            return null;
        }
        return ctx.getTerminalIds()
                .get(ThreadLocalRandom.current().nextInt(ctx.getTerminalIds().size()));
    }

    private record ShiftContext(UUID counterId, UUID terminalId, UUID cashierId, UUID shiftId) {}
}
