package com.flowledger.demo.generator;

import static com.flowledger.location.dto.LocationDtos.CashDrawerRequest;
import static com.flowledger.retail.dto.RetailDtos.CounterRequest;
import static com.flowledger.retail.dto.RetailDtos.StoreRequest;
import static com.flowledger.retail.dto.RetailDtos.TerminalRequest;
import static com.flowledger.warehouse.dto.WarehouseDtos.Create;

import com.flowledger.common.tenant.TenantContext;
import com.flowledger.demo.DemoSeedContext;
import com.flowledger.demo.scenario.DemoBlueprint;
import com.flowledger.demo.util.DemoFaker;
import com.flowledger.demo.util.DemoIsolatedWork;
import com.flowledger.demo.util.ProgressLogger;
import com.flowledger.location.service.CashDrawerService;
import com.flowledger.organization.dto.BranchDtos.BranchRequest;
import com.flowledger.organization.dto.BranchDtos.BranchResponse;
import com.flowledger.organization.service.BranchService;
import com.flowledger.retail.domain.StoreType;
import com.flowledger.retail.dto.RetailDtos.CounterResponse;
import com.flowledger.retail.dto.RetailDtos.StoreResponse;
import com.flowledger.retail.dto.RetailDtos.TerminalResponse;
import com.flowledger.retail.service.RetailStoreService;
import com.flowledger.warehouse.domain.WarehouseType;
import com.flowledger.warehouse.dto.WarehouseDtos.Response;
import com.flowledger.warehouse.service.WarehouseService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class LocationGenerator {
    private static final String[] CITIES = {"Bangalore", "Hyderabad", "Chennai", "Mumbai", "Pune", "Delhi"};
    private static final String[] STORE_TEMPLATES = {
        "Fresh Grocery", "Fashion Hub", "Electronics World", "Digital Experience", "Pharmacy"
    };

    private final BranchService branchService;
    private final WarehouseService warehouseService;
    private final RetailStoreService retailStoreService;
    private final CashDrawerService cashDrawerService;
    private final DemoFaker faker;
    private final DemoIsolatedWork isolated;

    public LocationGenerator(
            BranchService branchService,
            WarehouseService warehouseService,
            RetailStoreService retailStoreService,
            CashDrawerService cashDrawerService,
            DemoFaker faker,
            DemoIsolatedWork isolated) {
        this.branchService = branchService;
        this.warehouseService = warehouseService;
        this.retailStoreService = retailStoreService;
        this.cashDrawerService = cashDrawerService;
        this.faker = faker;
        this.isolated = isolated;
    }

    @Transactional
    public void generate(DemoSeedContext ctx, ProgressLogger progress) {
        DemoBlueprint blueprint = ctx.getBlueprint();
        TenantContext.set(ctx.getOrganizationId(), ctx.getAdminUserId());
        progress.stage("Creating branches, warehouses, and stores");

        int branchCount = blueprint.branchCount();
        int[] storesPerBranch = distribute(blueprint.storeCount(), branchCount);
        int globalStoreIndex = 0;

        for (int b = 0; b < branchCount; b++) {
            String city = CITIES[b % CITIES.length];
            boolean headOffice = b == 0;
            BranchResponse branch = branchService.create(new BranchRequest(
                    String.format("BR%03d", b + 1),
                    headOffice ? "Head Office — " + city : city + " Branch",
                    faker.faker().address().streetAddress(),
                    city,
                    city.equals("Delhi") ? "Delhi" : "Karnataka",
                    faker.faker().address().zipCode(),
                    "IN",
                    null,
                    null,
                    faker.phone(),
                    "branch" + (b + 1) + "@" + ctx.getScenario().slug() + ".demo",
                    true,
                    headOffice,
                    headOffice));
            ctx.getBranchIds().add(branch.id());

            UUID branchMainWhId = createBranchWarehouses(ctx, branch.id(), city, headOffice, b);

            for (int s = 0; s < storesPerBranch[b]; s++) {
                globalStoreIndex++;
                boolean onlineLast =
                        blueprint.workflow().onlineStore() && globalStoreIndex == blueprint.storeCount();
                createStore(ctx, blueprint, branch, city, branchMainWhId, globalStoreIndex, onlineLast);
            }
            progress.progress("Branches", b + 1, branchCount);
        }

        progress.done(String.format(
                "Locations (%d branches, %d warehouses, %d stores, %d terminals)",
                ctx.getBranchIds().size(),
                ctx.getWarehouseIds().size(),
                ctx.getStoreIds().size(),
                ctx.getTerminalIds().size()));
    }

    private UUID createBranchWarehouses(
            DemoSeedContext ctx, UUID branchId, String city, boolean headOffice, int branchIndex) {
        String prefix = "WH-BR" + String.format("%03d", branchIndex + 1);
        boolean firstBranch = branchIndex == 0;
        UUID mainId = warehouseService
                .create(new Create(
                        prefix + "-MAIN",
                        city + " Main Warehouse",
                        city + " industrial area",
                        "Store Manager",
                        faker.phone(),
                        firstBranch,
                        headOffice && firstBranch ? WarehouseType.CENTRAL : WarehouseType.BRANCH,
                        branchId,
                        null))
                .id();
        ctx.getWarehouseIds().add(mainId);

        for (String suffix : List.of("RET", "DMG", "TRN")) {
            WarehouseType type = "TRN".equals(suffix) ? WarehouseType.TRANSIT : WarehouseType.BRANCH;
            Response wh = warehouseService.create(new Create(
                    prefix + "-" + suffix,
                    city + " " + warehouseLabel(suffix),
                    null,
                    null,
                    null,
                    false,
                    type,
                    branchId,
                    null));
            ctx.getWarehouseIds().add(wh.id());
        }
        return mainId;
    }

    private void createStore(
            DemoSeedContext ctx,
            DemoBlueprint blueprint,
            BranchResponse branch,
            String city,
            UUID branchMainWhId,
            int storeIndex,
            boolean online) {
        String baseName = STORE_TEMPLATES[(storeIndex - 1) % STORE_TEMPLATES.length];
        String name = storeIndex > STORE_TEMPLATES.length ? baseName + " " + storeIndex : baseName;
        String code = "ST" + String.format("%04d", storeIndex);

        StoreResponse store = retailStoreService.createStore(new StoreRequest(
                code,
                name,
                branch.id(),
                null,
                online ? StoreType.ONLINE : StoreType.RETAIL,
                branchMainWhId,
                null,
                faker.faker().address().streetAddress(),
                city,
                branch.state(),
                faker.faker().address().zipCode(),
                "IN",
                faker.phone(),
                code.toLowerCase() + "@" + ctx.getScenario().slug() + ".demo",
                "ACTIVE",
                "INR",
                "Asia/Kolkata",
                false,
                true,
                online,
                blueprint.workflow().loyaltyEnabled(),
                false));
        ctx.getStoreIds().add(store.id());

        Response storeWh = warehouseService.create(new Create(
                code + "-WH",
                name + " Stock",
                store.address(),
                null,
                null,
                false,
                WarehouseType.STORE,
                branch.id(),
                store.id()));
        ctx.getWarehouseIds().add(storeWh.id());
        ctx.getStoreWarehouseIds().put(store.id(), storeWh.id());

        retailStoreService.updateStore(
                store.id(),
                new StoreRequest(
                        store.code(),
                        name,
                        branch.id(),
                        null,
                        online ? StoreType.ONLINE : StoreType.RETAIL,
                        storeWh.id(),
                        null,
                        store.address(),
                        city,
                        branch.state(),
                        store.postalCode(),
                        "IN",
                        store.phone(),
                        store.email(),
                        "ACTIVE",
                        "INR",
                        "Asia/Kolkata",
                        false,
                        true,
                        online,
                        blueprint.workflow().loyaltyEnabled(),
                        false));

        int counters = 2;
        List<UUID> counterIds = new ArrayList<>();
        for (int c = 1; c <= counters; c++) {
            CounterResponse counter = retailStoreService.createCounter(
                    new CounterRequest(store.id(), "C" + c, "Counter " + c, "ACTIVE"));
            counterIds.add(counter.id());
        }

        int terminals = 2;
        for (int t = 1; t <= terminals; t++) {
            UUID counterId = counterIds.get((t - 1) % counterIds.size());
            TerminalResponse terminal = retailStoreService.createTerminal(new TerminalRequest(
                    store.id(), counterId, "T" + t, "Terminal " + t, "DEV-" + storeIndex + "-" + t, "ACTIVE"));
            ctx.getTerminalIds().add(terminal.id());
            if (t == 1) {
                try {
                    isolated.run(() -> cashDrawerService.create(
                            new CashDrawerRequest(terminal.id(), "DR1", "Main Drawer", "ACTIVE")));
                } catch (RuntimeException ex) {
                    // optional retail setup
                }
            }
        }
    }

    private static String warehouseLabel(String suffix) {
        return switch (suffix) {
            case "RET" -> "Returns";
            case "DMG" -> "Damaged";
            case "TRN" -> "Transit";
            default -> "Warehouse";
        };
    }

    private static int[] distribute(int total, int buckets) {
        int[] out = new int[Math.max(1, buckets)];
        if (buckets <= 0) {
            return out;
        }
        int base = total / buckets;
        int rem = total % buckets;
        for (int i = 0; i < buckets; i++) {
            out[i] = base + (i < rem ? 1 : 0);
        }
        return out;
    }
}
