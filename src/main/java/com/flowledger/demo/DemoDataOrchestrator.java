package com.flowledger.demo;

import com.flowledger.common.tenant.TenantContext;
import com.flowledger.demo.config.DemoProperties;
import com.flowledger.demo.generator.CatalogGenerator;
import com.flowledger.demo.generator.InventoryGenerator;
import com.flowledger.demo.generator.LocationGenerator;
import com.flowledger.demo.generator.LoyaltyPromotionGenerator;
import com.flowledger.demo.generator.OrgBootstrapGenerator;
import com.flowledger.demo.generator.PartyGenerator;
import com.flowledger.demo.generator.PosSalesHistoryGenerator;
import com.flowledger.demo.generator.ProductImageSeeder;
import com.flowledger.demo.generator.PurchaseHistoryGenerator;
import com.flowledger.demo.generator.SupplierCatalogGenerator;
import com.flowledger.demo.generator.TransferScenarioGenerator;
import com.flowledger.demo.scenario.DemoBlueprint;
import com.flowledger.demo.scenario.DemoScenario;
import com.flowledger.demo.scenario.DemoScenarioRegistry;
import com.flowledger.demo.util.ProgressLogger;
import com.flowledger.demo.verify.ScenarioVerifier;
import com.flowledger.demo.job.DemoSeedProgressSink;
import com.flowledger.organization.repository.OrganizationRepository;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DemoDataOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(DemoDataOrchestrator.class);

    private final DemoProperties props;
    private final DemoScenarioRegistry registry;
    private final OrganizationRepository organizations;
    private final OrgBootstrapGenerator orgBootstrap;
    private final LocationGenerator locations;
    private final CatalogGenerator catalog;
    private final ProductImageSeeder productImages;
    private final PartyGenerator parties;
    private final SupplierCatalogGenerator supplierCatalog;
    private final InventoryGenerator inventory;
    private final PurchaseHistoryGenerator purchases;
    private final PosSalesHistoryGenerator sales;
    private final LoyaltyPromotionGenerator loyalty;
    private final TransferScenarioGenerator transfers;
    private final ScenarioVerifier verifier;

    public DemoDataOrchestrator(
            DemoProperties props,
            DemoScenarioRegistry registry,
            OrganizationRepository organizations,
            OrgBootstrapGenerator orgBootstrap,
            LocationGenerator locations,
            CatalogGenerator catalog,
            ProductImageSeeder productImages,
            PartyGenerator parties,
            SupplierCatalogGenerator supplierCatalog,
            InventoryGenerator inventory,
            PurchaseHistoryGenerator purchases,
            PosSalesHistoryGenerator sales,
            LoyaltyPromotionGenerator loyalty,
            TransferScenarioGenerator transfers,
            ScenarioVerifier verifier) {
        this.props = props;
        this.registry = registry;
        this.organizations = organizations;
        this.orgBootstrap = orgBootstrap;
        this.locations = locations;
        this.catalog = catalog;
        this.productImages = productImages;
        this.parties = parties;
        this.supplierCatalog = supplierCatalog;
        this.inventory = inventory;
        this.purchases = purchases;
        this.sales = sales;
        this.loyalty = loyalty;
        this.transfers = transfers;
        this.verifier = verifier;
    }

    /**
     * Runs the seed pipeline. Each generator commits in its own transaction. On failure the job
     * service purges any leftover organization — do not wrap this in one mega-TX (optional steps
     * that catch exceptions would mark a shared TX rollback-only).
     */
    public DemoSeedResult seed(DemoSeedRequest request) {
        return doSeed(request, null);
    }

    public DemoSeedResult seed(DemoSeedRequest request, DemoSeedProgressSink sink) {
        return doSeed(request, sink);
    }

    private DemoSeedResult doSeed(DemoSeedRequest request, DemoSeedProgressSink sink) {
        if (!props.isEnabled()) {
            throw new DemoDisabledException();
        }
        DemoScenario scenario = DemoScenario.fromCommand(
                request != null && request.scenario() != null ? request.scenario() : props.getScenario());
        DemoBlueprint blueprint = registry.require(scenario);
        Map<String, Integer> overrides = new HashMap<>(props.getOverrides());
        if (request != null && request.overrides() != null) {
            overrides.putAll(request.overrides());
        }
        blueprint = blueprint.withOverrides(overrides);
        String orgName = request != null && request.organizationName() != null && !request.organizationName().isBlank()
                ? request.organizationName()
                : blueprint.organizationName();
        // rebuild blueprint with optional org name override via meta — generators use blueprint.organizationName()
        if (!orgName.equals(blueprint.organizationName())) {
            blueprint = new DemoBlueprint(
                    blueprint.scenario(),
                    orgName,
                    blueprint.branchCount(),
                    blueprint.storeCount(),
                    blueprint.warehousesPerBranch(),
                    blueprint.terminalsPerStore(),
                    blueprint.productCount(),
                    blueprint.customerCount(),
                    blueprint.supplierCount(),
                    blueprint.salesCount(),
                    blueprint.purchaseCount(),
                    blueprint.catalogStrategy(),
                    blueprint.workflow(),
                    blueprint.estimatedMinutes());
        }

        String mode = request != null && request.mode() != null ? request.mode().toUpperCase() : "SKIP";
        var existing = organizations.findByNameIgnoreCase(orgName);
        if (existing.isPresent() && "SKIP".equals(mode)) {
            DemoSeedResult skipped = new DemoSeedResult(
                    scenario.command(),
                    "SKIPPED",
                    existing.get().getId(),
                    orgName,
                    "admin@" + scenario.slug() + ".demo",
                    "Organization already exists — skipped (mode=SKIP)",
                    blueprint.estimatedMinutes(),
                    0,
                    Map.of(),
                    Map.of("organization_exists", true));
            if (sink != null) {
                sink.onStage("Skipped — organization already exists");
            }
            return skipped;
        }
        if (existing.isPresent() && "RESET".equals(mode)) {
            if (!props.isAllowReset()) {
                throw new IllegalStateException(
                        "RESET refused — set flowledger.demo.allow-reset=true, or use a new organizationName");
            }
            // Destructive wipe is not implemented; seed a fresh sibling tenant instead.
            String resetName = orgName + " · reset "
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            orgName = resetName;
            blueprint = new DemoBlueprint(
                    blueprint.scenario(),
                    orgName,
                    blueprint.branchCount(),
                    blueprint.storeCount(),
                    blueprint.warehousesPerBranch(),
                    blueprint.terminalsPerStore(),
                    blueprint.productCount(),
                    blueprint.customerCount(),
                    blueprint.supplierCount(),
                    blueprint.salesCount(),
                    blueprint.purchaseCount(),
                    blueprint.catalogStrategy(),
                    blueprint.workflow(),
                    blueprint.estimatedMinutes());
            if (sink != null) {
                sink.onStage("Reset → seeding fresh copy: " + orgName);
            }
        }

        long start = System.currentTimeMillis();
        ProgressLogger progress = new ProgressLogger(log, scenario.command(), sink, 12);
        DemoSeedContext ctx = new DemoSeedContext();
        ctx.setScenario(scenario);
        ctx.setBlueprint(blueprint);

        try {
            progress.stage("Starting seed " + scenario.command());
            orgBootstrap.generate(ctx, progress);
            locations.generate(ctx, progress);
            catalog.generate(ctx, progress);
            productImages.generate(ctx, progress);
            parties.generate(ctx, progress);
            supplierCatalog.generate(ctx, progress);
            inventory.generate(ctx, progress);
            purchases.generate(ctx, progress);
            sales.generate(ctx, progress);
            loyalty.generate(ctx, progress);
            transfers.generate(ctx, progress);
            verifier.verify(ctx, progress);
        } catch (RuntimeException ex) {
            log.error("Demo seed failed for scenario {}", scenario.command(), ex);
            throw ex;
        } finally {
            TenantContext.clear();
        }

        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("branches", ctx.getBranchIds().size());
        counts.put("warehouses", ctx.getWarehouseIds().size());
        counts.put("stores", ctx.getStoreIds().size());
        counts.put("terminals", ctx.getTerminalIds().size());
        counts.put("products", ctx.getProductIds().size());
        counts.put("customers", ctx.getCustomerIds().size());
        counts.put("suppliers", ctx.getSupplierIds().size());
        counts.put("supplierCatalogLinks", ctx.getMeta().getOrDefault("supplierCatalogLinks", 0));
        counts.put("imagesUploaded", ctx.getMeta().getOrDefault("imagesUploaded", 0));
        counts.put("imagesReused", ctx.getMeta().getOrDefault("imagesReused", 0));

        return new DemoSeedResult(
                scenario.command(),
                "COMPLETED",
                ctx.getOrganizationId(),
                orgName,
                "admin@" + scenario.slug() + ".demo",
                "Demo seed completed",
                blueprint.estimatedMinutes(),
                System.currentTimeMillis() - start,
                counts,
                ctx.getChecks());
    }

    public static class DemoDisabledException extends RuntimeException {
        public DemoDisabledException() {
            super("Demo data seeding is disabled");
        }
    }
}
