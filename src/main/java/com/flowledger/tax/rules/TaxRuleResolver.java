package com.flowledger.tax.rules;

import com.flowledger.common.service.OrganizationScopedService;
import com.flowledger.product.entity.Product;
import com.flowledger.product.repository.ProductRepository;
import com.flowledger.tax.entity.OrganizationTaxSettings;
import com.flowledger.tax.entity.ProductCategoryTaxMapping;
import com.flowledger.tax.entity.ProductTaxMapping;
import com.flowledger.tax.entity.TaxCategory;
import com.flowledger.tax.entity.TaxRule;
import com.flowledger.tax.repository.OrganizationTaxSettingsRepository;
import com.flowledger.tax.repository.ProductCategoryTaxMappingRepository;
import com.flowledger.tax.repository.ProductTaxMappingRepository;
import com.flowledger.tax.repository.TaxCategoryRepository;
import com.flowledger.tax.repository.TaxRuleRepository;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class TaxRuleResolver extends OrganizationScopedService {
    private final ProductRepository productRepository;
    private final ProductTaxMappingRepository productTaxMappingRepository;
    private final ProductCategoryTaxMappingRepository productCategoryTaxMappingRepository;
    private final OrganizationTaxSettingsRepository settingsRepository;
    private final TaxCategoryRepository categoryRepository;
    private final TaxRuleRepository ruleRepository;

    public TaxRuleResolver(
            ProductRepository productRepository,
            ProductTaxMappingRepository productTaxMappingRepository,
            ProductCategoryTaxMappingRepository productCategoryTaxMappingRepository,
            OrganizationTaxSettingsRepository settingsRepository,
            TaxCategoryRepository categoryRepository,
            TaxRuleRepository ruleRepository) {
        this.productRepository = productRepository;
        this.productTaxMappingRepository = productTaxMappingRepository;
        this.productCategoryTaxMappingRepository = productCategoryTaxMappingRepository;
        this.settingsRepository = settingsRepository;
        this.categoryRepository = categoryRepository;
        this.ruleRepository = ruleRepository;
    }

    public Optional<TaxCategory> resolveCategory(
            UUID productId, UUID productCategoryId, UUID explicitCategoryId, String countryCode) {
        UUID org = orgId();
        if (explicitCategoryId != null) {
            return categoryRepository.findByIdAndOrganizationId(explicitCategoryId, org);
        }
        if (productId != null) {
            Optional<ProductTaxMapping> mapping =
                    productTaxMappingRepository.findByOrganizationIdAndProductId(org, productId);
            if (mapping.isPresent() && !mapping.get().isInheritFromCategory()) {
                return categoryRepository.findByIdAndOrganizationId(
                        mapping.get().getTaxCategoryId(), org);
            }
            Optional<Product> product = productRepository.findByIdAndOrganizationId(productId, org);
            if (product.isPresent() && product.get().getCategoryId() != null) {
                Optional<TaxCategory> fromCategory =
                        resolveFromProductCategory(product.get().getCategoryId());
                if (fromCategory.isPresent()) {
                    return fromCategory;
                }
            }
            if (mapping.isPresent()) {
                return categoryRepository.findByIdAndOrganizationId(
                        mapping.get().getTaxCategoryId(), org);
            }
        }
        if (productCategoryId != null) {
            Optional<TaxCategory> fromCategory = resolveFromProductCategory(productCategoryId);
            if (fromCategory.isPresent()) {
                return fromCategory;
            }
        }
        Optional<OrganizationTaxSettings> settings = settingsRepository.findById(org);
        if (settings.isPresent() && settings.get().getDefaultTaxCategoryId() != null) {
            return categoryRepository.findByIdAndOrganizationId(settings.get().getDefaultTaxCategoryId(), org);
        }
        if (countryCode != null && !countryCode.isBlank()) {
            return categoryRepository.findByOrganizationIdAndActiveTrue(org).stream()
                    .filter(c -> countryCode.equalsIgnoreCase(c.getCountryCode()))
                    .findFirst();
        }
        return Optional.empty();
    }

    public TaxRule resolveRule(UUID categoryId, String countryCode, String stateCode, LocalDate date) {
        TaxCategory category =
                required(categoryRepository.findByIdAndOrganizationId(categoryId, orgId()), "Tax category");
        String country = countryCode == null || countryCode.isBlank() ? category.getCountryCode() : countryCode;
        LocalDate effectiveDate = date == null ? LocalDate.now() : date;
        return ruleRepository
                .findApplicableRule(orgId(), categoryId, country, normalizeState(stateCode), effectiveDate)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No applicable tax rule found for category " + category.getCode()));
    }

    public ResolvedTax resolve(
            UUID productId,
            UUID productCategoryId,
            UUID explicitCategoryId,
            String countryCode,
            String stateCode,
            LocalDate date) {
        TaxCategory category = resolveCategory(productId, productCategoryId, explicitCategoryId, countryCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No tax category resolved"));
        TaxRule rule = resolveRule(category.getId(), countryCode, stateCode, date);
        return new ResolvedTax(category, rule);
    }

    private Optional<TaxCategory> resolveFromProductCategory(UUID productCategoryId) {
        return productCategoryTaxMappingRepository
                .findByOrganizationIdAndProductCategoryId(orgId(), productCategoryId)
                .map(ProductCategoryTaxMapping::getTaxCategoryId)
                .flatMap(id -> categoryRepository.findByIdAndOrganizationId(id, orgId()));
    }

    private static String normalizeState(String stateCode) {
        return stateCode == null ? "" : stateCode.trim();
    }

    public record ResolvedTax(TaxCategory category, TaxRule rule) {}
}
