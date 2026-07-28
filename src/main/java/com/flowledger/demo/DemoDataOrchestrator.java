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
import com.flowledger.demo.generator.PurchaseHistoryGenerator;
import com.flowledger.demo.generator.TransferScenarioGenerator;
import com.flowledger.demo.scenario.DemoBlueprint;
import com.flowledger.demo.scenario.DemoScenario;
import com.flowledger.demo.scenario.DemoScenarioRegistry;
import com.flowledger.demo.util.ProgressLogger;
import com.flowledger.demo.verify.ScenarioVerifier;
import com.flowledger.organization.repository.OrganizationRepository;
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
    private final PartyGenerator parties;
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
            PartyGenerator parties,
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
        this.parties = parties;
        this.inventory = inventory;
        this.purchases = purchases;
        this.sales = sales;
        this.loyalty = loyalty;
        this.transfers = transfers;
        this.verifier = verifier;
    }

    public DemoSeedResult seed(DemoSeedRequest request) {
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
            return new DemoSeedResult(
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
        }
        if (existing.isPresent() && "RESET".equals(mode) && !props.isAllowReset()) {
            throw new IllegalStateException("RESET refused — set flowledger.demo.allow-reset=true");
        }

        long start = System.currentTimeMillis();
        ProgressLogger progress = new ProgressLogger(log, scenario.command());
        DemoSeedContext ctx = new DemoSeedContext();
        ctx.setScenario(scenario);
        ctx.setBlueprint(blueprint);

        try {
            progress.stage("Starting seed " + scenario.command());
            orgBootstrap.generate(ctx, progress);
            locations.generate(ctx, progress);
            catalog.generate(ctx, progress);
            parties.generate(ctx, progress);
            inventory.generate(ctx, progress);
            purchases.generate(ctx, progress);
            sales.generate(ctx, progress);
            loyalty.generate(ctx, progress);
            transfers.generate(ctx, progress);
            verifier.verify(ctx, progress);
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
