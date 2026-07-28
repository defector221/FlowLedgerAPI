package com.flowledger.demo.generator;

import com.flowledger.barcode.dto.ProductBarcodeDtos.CreateBarcodeRequest;
import com.flowledger.barcode.service.ProductBarcodeManagementService;
import com.flowledger.common.tenant.TenantContext;
import com.flowledger.demo.DemoSeedContext;
import com.flowledger.demo.catalog.CatalogStrategy;
import com.flowledger.demo.catalog.CatalogStrategyRegistry;
import com.flowledger.demo.scenario.DemoBlueprint;
import com.flowledger.demo.util.DemoFaker;
import com.flowledger.demo.util.DemoIsolatedWork;
import com.flowledger.demo.util.ProgressLogger;
import com.flowledger.product.dto.CategoryDtos;
import com.flowledger.product.dto.ProductDtos.Create;
import com.flowledger.product.dto.TaxRateDtos;
import com.flowledger.product.dto.UnitDtos;
import com.flowledger.product.entity.SplitStrategy;
import com.flowledger.product.entity.TaxType;
import com.flowledger.product.service.CategoryService;
import com.flowledger.product.service.ProductService;
import com.flowledger.product.service.TaxRateService;
import com.flowledger.product.service.UnitService;
import com.flowledger.retail.dto.RetailDtos.BrandRequest;
import com.flowledger.retail.dto.RetailDtos.VariantRequest;
import com.flowledger.retail.service.RetailCatalogService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CatalogGenerator {
    private static final Logger log = LoggerFactory.getLogger(CatalogGenerator.class);
    private static final int BATCH_SIZE = 50;
    private static final BigDecimal[] GST_RATES = {
        BigDecimal.ZERO, new BigDecimal("5"), new BigDecimal("12"), new BigDecimal("18"), new BigDecimal("28")
    };

    private final UnitService unitService;
    private final TaxRateService taxRateService;
    private final CategoryService categoryService;
    private final ProductService productService;
    private final CatalogStrategyRegistry catalogStrategies;
    private final ProductBarcodeManagementService barcodeManagement;
    private final RetailCatalogService retailCatalogService;
    private final DemoFaker faker;
    private final DemoIsolatedWork isolated;

    public CatalogGenerator(
            UnitService unitService,
            TaxRateService taxRateService,
            CategoryService categoryService,
            ProductService productService,
            CatalogStrategyRegistry catalogStrategies,
            ProductBarcodeManagementService barcodeManagement,
            RetailCatalogService retailCatalogService,
            DemoFaker faker,
            DemoIsolatedWork isolated) {
        this.unitService = unitService;
        this.taxRateService = taxRateService;
        this.categoryService = categoryService;
        this.productService = productService;
        this.catalogStrategies = catalogStrategies;
        this.barcodeManagement = barcodeManagement;
        this.retailCatalogService = retailCatalogService;
        this.faker = faker;
        this.isolated = isolated;
    }

    @Transactional
    public void generate(DemoSeedContext ctx, ProgressLogger progress) {
        DemoBlueprint blueprint = ctx.getBlueprint();
        TenantContext.set(ctx.getOrganizationId(), ctx.getAdminUserId());
        progress.stage("Seeding catalog");

        Map<String, UUID> units = ensureUnits(ctx);
        Map<BigDecimal, UUID> taxRates = ensureTaxRates(ctx);
        CatalogStrategy strategy = catalogStrategies.require(blueprint.catalogStrategy());

        List<UUID> categoryIds = new ArrayList<>();
        for (String name : strategy.categoryNames()) {
            categoryIds.add(categoryService.create(new CategoryDtos.Create(name, null, null)).id());
        }

        int target = blueprint.productCount();
        List<String> seeds = strategy.productNameSeeds();
        List<String> brands = strategy.brandNames();
        String skuPrefix = blueprint.catalogStrategy().name().substring(0, 3);

        for (int i = 1; i <= target; i++) {
            String seed = seeds.get((i - 1) % seeds.size());
            String brand = brands.get((i - 1) % brands.size());
            UUID categoryId = categoryIds.get((i - 1) % categoryIds.size());
            UUID unitId = pickUnit(units, seed);
            UUID taxRateId = pickTaxRate(taxRates);

            BigDecimal purchase = randomPrice(10, 500);
            BigDecimal selling = purchase.multiply(new BigDecimal("1.25")).setScale(2, RoundingMode.HALF_UP);
            BigDecimal mrp = selling.multiply(new BigDecimal("1.1")).setScale(2, RoundingMode.HALF_UP);
            String barcode = faker.ean13();

            var created = productService.create(new Create(
                    faker.sku(skuPrefix, i),
                    seed + " " + brand + " " + i,
                    unitId,
                    "PRODUCT",
                    barcode,
                    "Demo catalog item",
                    categoryId,
                    brand,
                    "1001" + String.format("%04d", 1000 + (i % 9000)),
                    purchase,
                    selling,
                    mrp,
                    taxRateId,
                    null,
                    null,
                    // Keep thresholds well below seeded store opening stock (100+).
                    new BigDecimal("10"),
                    null,
                    new BigDecimal("25"),
                    false,
                    strategy.preferSerial() || blueprint.workflow().serialTrackingHeavy(),
                    false,
                    null));
            ctx.getProductIds().add(created.id());

            try {
                isolated.run(() -> barcodeManagement.create(
                        created.id(), new CreateBarcodeRequest(barcode, "EAN13", true, null)));
            } catch (RuntimeException ex) {
                log.debug("Optional barcode skipped for {}: {}", created.id(), ex.getMessage());
            }

            if (i % BATCH_SIZE == 0 || i == target) {
                progress.progress("Products", i, target);
            }
        }

        if (blueprint.workflow().fashionVariants() || strategy.preferVariants()) {
            seedFashionVariants(ctx, strategy, brands, skuPrefix);
        }

        progress.done("Catalog (" + ctx.getProductIds().size() + " products)");
    }

    private Map<String, UUID> ensureUnits(DemoSeedContext ctx) {
        Map<String, UUID> out = new HashMap<>();
        for (String code : List.of("PCS", "KG", "LTR", "LITRE")) {
            unitService.list().stream()
                    .filter(u -> code.equalsIgnoreCase(u.code()))
                    .findFirst()
                    .ifPresent(u -> out.putIfAbsent(normalizeUnitCode(u.code()), u.id()));
        }
        if (!out.containsKey("PCS")) {
            out.put("PCS", unitService.create(new UnitDtos.Create("PCS", "Pieces")).id());
        }
        if (!out.containsKey("KG")) {
            out.put("KG", unitService.create(new UnitDtos.Create("KG", "Kilogram")).id());
        }
        if (!out.containsKey("LTR")) {
            UUID id = out.containsKey("LITRE")
                    ? out.get("LITRE")
                    : unitService.create(new UnitDtos.Create("LTR", "Litre")).id();
            out.put("LTR", id);
        }
        ctx.getUnitIds().addAll(out.values());
        return out;
    }

    private Map<BigDecimal, UUID> ensureTaxRates(DemoSeedContext ctx) {
        Map<BigDecimal, UUID> byRate = new HashMap<>();
        List<TaxRateDtos.Response> existing = taxRateService.list();
        for (BigDecimal rate : GST_RATES) {
            existing.stream()
                    .filter(t -> t.rate().compareTo(rate) == 0)
                    .findFirst()
                    .ifPresent(t -> byRate.put(rate, t.id()));
        }
        for (BigDecimal rate : GST_RATES) {
            if (byRate.containsKey(rate)) {
                continue;
            }
            TaxRateDtos.Response created = taxRateService.create(new TaxRateDtos.Create(
                    "GST " + rate.stripTrailingZeros().toPlainString() + "%",
                    TaxType.GST,
                    SplitStrategy.PLACE_OF_SUPPLY,
                    new BigDecimal("50"),
                    new BigDecimal("50"),
                    rate,
                    BigDecimal.ZERO));
            byRate.put(rate, created.id());
        }
        ctx.getTaxRateIds().addAll(byRate.values());
        return byRate;
    }

    private void seedFashionVariants(DemoSeedContext ctx, CatalogStrategy strategy, List<String> brands, String skuPrefix) {
        try {
            isolated.run(() -> retailCatalogService.createBrand(new BrandRequest("DEMO", "Demo Brand")));
        } catch (RuntimeException ex) {
            log.debug("Brand seed skipped: {}", ex.getMessage());
        }
        String[] sizes = {"S", "M", "L", "XL"};
        String[] colors = {"Black", "Blue", "Red", "White"};
        int limit = Math.min(20, ctx.getProductIds().size());
        for (int i = 0; i < limit; i++) {
            UUID productId = ctx.getProductIds().get(i);
            final int idx = i;
            try {
                isolated.run(() -> retailCatalogService.createVariant(new VariantRequest(
                        productId,
                        faker.sku(skuPrefix + "V", idx + 1),
                        faker.ean13(),
                        colors[idx % colors.length],
                        sizes[idx % sizes.length],
                        null,
                        null,
                        null,
                        brands.get(idx % brands.size()),
                        new BigDecimal("999"),
                        new BigDecimal("1299"),
                        true)));
            } catch (RuntimeException ex) {
                log.debug("Variant seed skipped for {}: {}", productId, ex.getMessage());
            }
        }
    }

    private static UUID pickUnit(Map<String, UUID> units, String seed) {
        String key = seed.toLowerCase().contains("kg") || seed.toLowerCase().contains("rice") ? "KG" : "PCS";
        if (seed.toLowerCase().contains("milk")
                || seed.toLowerCase().contains("oil")
                || seed.toLowerCase().contains("juice")) {
            key = "LTR";
        }
        return units.getOrDefault(key, units.get("PCS"));
    }

    private static UUID pickTaxRate(Map<BigDecimal, UUID> taxRates) {
        BigDecimal[] keys = taxRates.keySet().toArray(BigDecimal[]::new);
        return taxRates.get(keys[ThreadLocalRandom.current().nextInt(keys.length)]);
    }

    private static BigDecimal randomPrice(int min, int max) {
        return BigDecimal.valueOf(ThreadLocalRandom.current().nextInt(min, max + 1));
    }

    private static String normalizeUnitCode(String code) {
        return "LITRE".equalsIgnoreCase(code) ? "LTR" : code.toUpperCase();
    }
}
