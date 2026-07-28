package com.flowledger.demo.generator;

import com.flowledger.common.tenant.TenantContext;
import com.flowledger.demo.DemoSeedContext;
import com.flowledger.demo.util.ProgressLogger;
import com.flowledger.organization.entity.Organization;
import com.flowledger.organization.repository.OrganizationRepository;
import com.flowledger.product.entity.TaxType;
import com.flowledger.tax.domain.JurisdictionType;
import com.flowledger.tax.domain.TaxProviderCode;
import com.flowledger.tax.dto.TaxAdminDtos.*;
import com.flowledger.tax.entity.ProductCategoryTaxMapping;
import com.flowledger.tax.entity.ProductTaxMapping;
import com.flowledger.tax.repository.ProductCategoryTaxMappingRepository;
import com.flowledger.tax.repository.ProductTaxMappingRepository;
import com.flowledger.tax.service.TaxAdminService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TaxSetupGenerator {
    private static final String COUNTRY = "IN";
    private static final String ORG_STATE = "Karnataka";
    private static final String ORG_STATE_CODE = "29";
    private static final String DEMO_GSTIN = "29AABCU9603R1ZM";

    private static final List<CategorySpec> CATEGORIES = List.of(
            new CategorySpec("GST_5", "GST 5%", "Standard rate 5%"),
            new CategorySpec("GST_12", "GST 12%", "Standard rate 12%"),
            new CategorySpec("GST_18", "GST 18%", "Standard rate 18%"),
            new CategorySpec("GST_28", "GST 28%", "Standard rate 28%"),
            new CategorySpec("EXEMPT", "Exempt", "Exempt supplies"),
            new CategorySpec("ZERO_RATED", "Zero rated", "Zero-rated exports"));

    private static final List<JurisdictionSpec> JURISDICTIONS = List.of(
            new JurisdictionSpec(COUNTRY, null, COUNTRY, JurisdictionType.GST),
            new JurisdictionSpec(COUNTRY, "Karnataka", "29", JurisdictionType.GST),
            new JurisdictionSpec(COUNTRY, "Maharashtra", "27", JurisdictionType.GST),
            new JurisdictionSpec(COUNTRY, "Tamil Nadu", "33", JurisdictionType.GST),
            new JurisdictionSpec(COUNTRY, "Delhi", "07", JurisdictionType.GST));

    private static final Map<String, String> SEGMENT_TAX_CODES = Map.of(
            "grocery", "GST_5",
            "electronics", "GST_18",
            "fashion", "GST_12",
            "pharmacy", "GST_12",
            "wholesale", "GST_5",
            "default", "GST_18");

    private static final Map<String, HsnSpec> HSN_BY_SEGMENT = Map.of(
            "grocery", new HsnSpec("10063010", "Basmati rice", "HSN"),
            "electronics", new HsnSpec("85171200", "Smartphones", "HSN"),
            "fashion", new HsnSpec("61091000", "Cotton T-shirts", "HSN"),
            "pharmacy", new HsnSpec("30049099", "Medicaments", "HSN"),
            "wholesale", new HsnSpec("19053100", "Sweet biscuits", "HSN"),
            "default", new HsnSpec("99979990", "Miscellaneous", "HSN"));

    private final TaxAdminService taxAdminService;
    private final OrganizationRepository organizations;
    private final ProductCategoryTaxMappingRepository categoryTaxMappingRepository;
    private final ProductTaxMappingRepository productTaxMappingRepository;

    public TaxSetupGenerator(
            TaxAdminService taxAdminService,
            OrganizationRepository organizations,
            ProductCategoryTaxMappingRepository categoryTaxMappingRepository,
            ProductTaxMappingRepository productTaxMappingRepository) {
        this.taxAdminService = taxAdminService;
        this.organizations = organizations;
        this.categoryTaxMappingRepository = categoryTaxMappingRepository;
        this.productTaxMappingRepository = productTaxMappingRepository;
    }

    @Transactional
    public void generate(DemoSeedContext ctx, ProgressLogger progress) {
        TenantContext.set(ctx.getOrganizationId(), ctx.getAdminUserId());
        progress.stage("Seeding tax engine");

        Organization org = organizations
                .findById(ctx.getOrganizationId())
                .orElseThrow(() -> new IllegalStateException("Organization missing for tax setup"));
        ensureOrganizationTaxProfile(org);

        LocalDate effectiveFrom = resolveEffectiveFrom(org.getFinancialYearStart());
        int jurisdictions = seedJurisdictions();
        int categories = seedCategories(ctx);
        int rules = seedRules(ctx, effectiveFrom);
        int hsnCodes = seedHsnSac(ctx);
        seedSettings(ctx);
        seedSegmentMappings(ctx);

        ctx.getMeta().put("taxJurisdictions", jurisdictions);
        ctx.getMeta().put("taxCategories", categories);
        ctx.getMeta().put("taxRules", rules);
        ctx.getMeta().put("hsnSacCodes", hsnCodes);

        progress.done(String.format(
                "Tax setup (%d jurisdictions, %d categories, %d rules)", jurisdictions, categories, rules));
    }

    public void mapProductCategory(DemoSeedContext ctx, UUID productCategoryId, String segment) {
        UUID taxCategoryId = taxCategoryIdForSegment(ctx, segment);
        if (taxCategoryId == null) {
            return;
        }
        if (categoryTaxMappingRepository
                .findByOrganizationIdAndProductCategoryId(ctx.getOrganizationId(), productCategoryId)
                .isPresent()) {
            return;
        }
        ProductCategoryTaxMapping mapping = new ProductCategoryTaxMapping();
        mapping.setOrganizationId(ctx.getOrganizationId());
        mapping.setProductCategoryId(productCategoryId);
        mapping.setTaxCategoryId(taxCategoryId);
        categoryTaxMappingRepository.save(mapping);
        incrementMeta(ctx, "productCategoryTaxMappings");
    }

    public void mapProduct(DemoSeedContext ctx, UUID productId, String segment, boolean inheritFromCategory) {
        UUID taxCategoryId = taxCategoryIdForSegment(ctx, segment);
        if (taxCategoryId == null) {
            return;
        }
        if (productTaxMappingRepository
                .findByOrganizationIdAndProductId(ctx.getOrganizationId(), productId)
                .isPresent()) {
            return;
        }
        ProductTaxMapping mapping = new ProductTaxMapping();
        mapping.setOrganizationId(ctx.getOrganizationId());
        mapping.setProductId(productId);
        mapping.setTaxCategoryId(taxCategoryId);
        mapping.setInheritFromCategory(inheritFromCategory);
        productTaxMappingRepository.save(mapping);
        ctx.setProductsWithTaxMapping(ctx.getProductsWithTaxMapping() + 1);
    }

    public static String resolveSegment(String categoryName, com.flowledger.demo.catalog.CatalogStrategyId strategyId) {
        String lower = categoryName == null ? "" : categoryName.toLowerCase();
        return switch (strategyId) {
            case GROCERY -> "grocery";
            case ELECTRONICS -> "electronics";
            case FASHION -> "fashion";
            case PHARMACY -> "pharmacy";
            case WHOLESALE -> "wholesale";
            case MIXED_RETAIL -> resolveMixedRetailSegment(lower);
        };
    }

    public static String hsnForSegment(DemoSeedContext ctx, String segment) {
        String key = segment == null || segment.isBlank() ? "default" : segment.toLowerCase();
        return ctx.getHsnSacByCategory()
                .getOrDefault(
                        key,
                        HSN_BY_SEGMENT
                                .getOrDefault(key, HSN_BY_SEGMENT.get("default"))
                                .code());
    }

    public static BigDecimal legacyRateForSegment(String segment) {
        String taxCode = SEGMENT_TAX_CODES.getOrDefault(segment == null ? "default" : segment.toLowerCase(), "GST_18");
        return switch (taxCode) {
            case "GST_5" -> new BigDecimal("5");
            case "GST_12" -> new BigDecimal("12");
            case "GST_28" -> new BigDecimal("28");
            case "EXEMPT", "ZERO_RATED" -> BigDecimal.ZERO;
            default -> new BigDecimal("18");
        };
    }

    private void ensureOrganizationTaxProfile(Organization org) {
        boolean changed = false;
        if (org.getGstin() == null || org.getGstin().isBlank()) {
            org.setGstin(DEMO_GSTIN);
            changed = true;
        }
        if (org.getStateCode() == null || org.getStateCode().isBlank()) {
            org.setStateCode(ORG_STATE_CODE);
            changed = true;
        }
        if (org.getState() == null || org.getState().isBlank()) {
            org.setState(ORG_STATE);
            changed = true;
        }
        if (changed) {
            organizations.save(org);
        }
    }

    private int seedJurisdictions() {
        int created = 0;
        for (JurisdictionSpec spec : JURISDICTIONS) {
            if (taxAdminService.listJurisdictions().stream()
                    .anyMatch(j -> j.code().equalsIgnoreCase(spec.code())
                            && j.country().equalsIgnoreCase(spec.country()))) {
                continue;
            }
            taxAdminService.createJurisdiction(
                    new JurisdictionRequest(spec.country(), spec.state(), spec.code(), spec.type(), true));
            created++;
        }
        return created;
    }

    private int seedCategories(DemoSeedContext ctx) {
        int created = 0;
        for (CategorySpec spec : CATEGORIES) {
            CategoryResponse existing = taxAdminService.listCategories().stream()
                    .filter(c -> c.code().equalsIgnoreCase(spec.code()))
                    .findFirst()
                    .orElse(null);
            if (existing != null) {
                ctx.getTaxCategoryIds().put(spec.code(), existing.id());
                continue;
            }
            CategoryResponse category = taxAdminService.createCategory(
                    new CategoryRequest(spec.code(), spec.name(), spec.description(), COUNTRY, true));
            ctx.getTaxCategoryIds().put(spec.code(), category.id());
            created++;
        }
        return created;
    }

    private int seedRules(DemoSeedContext ctx, LocalDate effectiveFrom) {
        int created = 0;
        for (CategorySpec spec : CATEGORIES) {
            UUID categoryId = ctx.getTaxCategoryIds().get(spec.code());
            if (categoryId == null) {
                continue;
            }
            String ruleKey = spec.code();
            if (ctx.getTaxRuleByCode().containsKey(ruleKey)) {
                continue;
            }
            List<RuleResponse> existing = taxAdminService.listRules(categoryId);
            RuleResponse active = existing.stream()
                    .filter(RuleResponse::active)
                    .filter(r -> r.countryCode().equalsIgnoreCase(COUNTRY))
                    .filter(r -> r.stateCode() == null || r.stateCode().isBlank())
                    .findFirst()
                    .orElse(null);
            if (active != null) {
                ctx.getTaxRuleByCode().put(ruleKey, active.id());
                continue;
            }
            RuleRates rates = ratesFor(spec.code());
            RuleResponse rule = taxAdminService.createRuleVersion(new RuleRequest(
                    categoryId,
                    TaxType.GST,
                    COUNTRY,
                    null,
                    rates.cgst(),
                    rates.sgst(),
                    rates.igst(),
                    BigDecimal.ZERO,
                    effectiveFrom,
                    null,
                    false,
                    0,
                    false,
                    rates.zeroRated(),
                    rates.exempt(),
                    false,
                    "FY" + effectiveFrom.getYear() + "-" + spec.code()));
            ctx.getTaxRuleByCode().put(ruleKey, rule.id());
            created++;
        }
        return created;
    }

    private int seedHsnSac(DemoSeedContext ctx) {
        int created = 0;
        for (Map.Entry<String, HsnSpec> entry : HSN_BY_SEGMENT.entrySet()) {
            HsnSpec spec = entry.getValue();
            ctx.getHsnSacByCategory().put(entry.getKey(), spec.code());
            if (taxAdminService.listHsnSac().stream().anyMatch(h -> h.code().equalsIgnoreCase(spec.code()))) {
                continue;
            }
            taxAdminService.createHsnSac(new HsnSacRequest(spec.code(), spec.description(), spec.type(), true));
            created++;
        }
        return created;
    }

    private void seedSettings(DemoSeedContext ctx) {
        UUID defaultCategoryId = ctx.getTaxCategoryIds().get("GST_18");
        taxAdminService.upsertSettings(new SettingsRequest(defaultCategoryId, TaxProviderCode.IndiaGST, 2, "HALF_UP"));
    }

    private void seedSegmentMappings(DemoSeedContext ctx) {
        Map<String, String> segmentTaxCodes = new LinkedHashMap<>(SEGMENT_TAX_CODES);
        ctx.getMeta().put("categoryTaxSegmentCodes", segmentTaxCodes);
    }

    private static UUID taxCategoryIdForSegment(DemoSeedContext ctx, String segment) {
        String key = segment == null || segment.isBlank() ? "default" : segment.toLowerCase();
        String taxCode = SEGMENT_TAX_CODES.getOrDefault(key, SEGMENT_TAX_CODES.get("default"));
        return ctx.getTaxCategoryIds().get(taxCode);
    }

    private static RuleRates ratesFor(String code) {
        return switch (code) {
            case "GST_5" ->
                new RuleRates(new BigDecimal("2.5"), new BigDecimal("2.5"), new BigDecimal("5"), false, false);
            case "GST_12" ->
                new RuleRates(new BigDecimal("6"), new BigDecimal("6"), new BigDecimal("12"), false, false);
            case "GST_18" ->
                new RuleRates(new BigDecimal("9"), new BigDecimal("9"), new BigDecimal("18"), false, false);
            case "GST_28" ->
                new RuleRates(new BigDecimal("14"), new BigDecimal("14"), new BigDecimal("28"), false, false);
            case "EXEMPT" -> new RuleRates(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false, true);
            case "ZERO_RATED" -> new RuleRates(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, true, false);
            default -> new RuleRates(new BigDecimal("9"), new BigDecimal("9"), new BigDecimal("18"), false, false);
        };
    }

    private static String resolveMixedRetailSegment(String lower) {
        if (lower.contains("grocery")
                || lower.contains("dairy")
                || lower.contains("beverage")
                || lower.contains("snack")
                || lower.contains("personal care")
                || lower.contains("home")) {
            return "grocery";
        }
        if (lower.contains("electronic") || lower.contains("digital")) {
            return "electronics";
        }
        if (lower.contains("fashion") || lower.contains("men") || lower.contains("women")) {
            return "fashion";
        }
        return "default";
    }

    private static LocalDate resolveEffectiveFrom(String financialYearStart) {
        LocalDate today = LocalDate.now();
        if (financialYearStart == null || financialYearStart.isBlank()) {
            return today;
        }
        try {
            String[] parts = financialYearStart.split("-");
            int month = Integer.parseInt(parts[0].trim());
            int day = Integer.parseInt(parts[1].trim());
            LocalDate candidate = LocalDate.of(today.getYear(), month, day);
            if (candidate.isAfter(today)) {
                candidate = candidate.minusYears(1);
            }
            return candidate;
        } catch (RuntimeException ex) {
            return today;
        }
    }

    private static void incrementMeta(DemoSeedContext ctx, String key) {
        Object current = ctx.getMeta().getOrDefault(key, 0);
        int next = current instanceof Number n ? n.intValue() + 1 : 1;
        ctx.getMeta().put(key, next);
    }

    private record CategorySpec(String code, String name, String description) {}

    private record JurisdictionSpec(String country, String state, String code, JurisdictionType type) {}

    private record HsnSpec(String code, String description, String type) {}

    private record RuleRates(BigDecimal cgst, BigDecimal sgst, BigDecimal igst, boolean zeroRated, boolean exempt) {}
}
