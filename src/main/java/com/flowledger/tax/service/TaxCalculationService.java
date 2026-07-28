package com.flowledger.tax.service;

import com.flowledger.common.service.OrganizationScopedService;
import com.flowledger.common.tenant.TenantContext;
import com.flowledger.product.entity.SplitStrategy;
import com.flowledger.product.entity.TaxType;
import com.flowledger.tax.domain.PricingMode;
import com.flowledger.tax.domain.TaxProviderCode;
import com.flowledger.tax.dto.TaxCalculationDtos.*;
import com.flowledger.tax.entity.OrganizationTaxSettings;
import com.flowledger.tax.entity.TaxCategory;
import com.flowledger.tax.entity.TaxRule;
import com.flowledger.tax.provider.TaxCalculationContext;
import com.flowledger.tax.provider.TaxProvider;
import com.flowledger.tax.provider.TaxProviderContext;
import com.flowledger.tax.repository.OrganizationTaxSettingsRepository;
import com.flowledger.tax.repository.TaxRuleRepository;
import com.flowledger.tax.rules.TaxRuleResolver;
import com.flowledger.tax.rules.TaxRuleResolver.ResolvedTax;
import com.flowledger.tax.strategy.TaxProviderRegistry;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TaxCalculationService extends OrganizationScopedService {
    private final TaxRuleResolver ruleResolver;
    private final TaxRuleRepository ruleRepository;
    private final OrganizationTaxSettingsRepository settingsRepository;
    private final TaxProviderRegistry providerRegistry;

    public TaxCalculationService(
            TaxRuleResolver ruleResolver,
            TaxRuleRepository ruleRepository,
            OrganizationTaxSettingsRepository settingsRepository,
            TaxProviderRegistry providerRegistry) {
        this.ruleResolver = ruleResolver;
        this.ruleRepository = ruleRepository;
        this.settingsRepository = settingsRepository;
        this.providerRegistry = providerRegistry;
    }

    public TaxResult calculateTax(TaxCalculationRequest request) {
        ResolvedTax resolved = ruleResolver.resolve(
                request.productId(),
                request.productCategoryId(),
                request.taxCategoryId(),
                request.countryCode(),
                request.placeOfSupplyStateCode(),
                request.effectiveDate());
        return calculateWithRule(request, resolved.category(), resolved.rule());
    }

    public TaxResult calculateLineTax(
            LineTaxRequest line,
            String organizationStateCode,
            String placeOfSupplyStateCode,
            String countryCode,
            LocalDate effectiveDate) {
        TaxCalculationRequest request = new TaxCalculationRequest(
                organizationStateCode,
                placeOfSupplyStateCode,
                countryCode,
                effectiveDate,
                line.productId(),
                line.productCategoryId(),
                line.taxCategoryId(),
                line.quantity(),
                line.rate(),
                line.discount(),
                null,
                line.taxType(),
                line.splitStrategy(),
                line.cgstSharePercent(),
                line.sgstSharePercent());
        return calculateTax(request);
    }

    public TaxSummary calculateDocumentTax(DocumentTaxRequest request) {
        LocalDate date = request.effectiveDate() == null ? LocalDate.now() : request.effectiveDate();
        List<TaxResult> lines = new ArrayList<>();
        BigDecimal taxable = BigDecimal.ZERO;
        BigDecimal cgst = BigDecimal.ZERO;
        BigDecimal sgst = BigDecimal.ZERO;
        BigDecimal igst = BigDecimal.ZERO;
        BigDecimal cess = BigDecimal.ZERO;
        BigDecimal other = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;

        for (LineTaxRequest line : request.lines()) {
            TaxResult result = calculateLineTax(
                    line,
                    request.organizationStateCode(),
                    request.placeOfSupplyStateCode(),
                    request.countryCode(),
                    date);
            lines.add(result);
            taxable = taxable.add(nullSafe(result.taxableAmount()));
            cgst = cgst.add(nullSafe(result.cgstAmount()));
            sgst = sgst.add(nullSafe(result.sgstAmount()));
            igst = igst.add(nullSafe(result.igstAmount()));
            cess = cess.add(nullSafe(result.cessAmount()));
            other = other.add(nullSafe(result.otherTaxAmount()));
            total = total.add(nullSafe(result.lineTotal()));
        }

        return new TaxSummary(taxable, cgst, sgst, igst, cess, other, total, List.copyOf(lines));
    }

    public TaxRule findApplicableRule(RuleLookupRequest request) {
        UUID categoryId = ruleResolver
                .resolveCategory(
                        request.productId(),
                        request.productCategoryId(),
                        request.taxCategoryId(),
                        request.countryCode())
                .map(TaxCategory::getId)
                .orElseThrow();
        return ruleRepository
                .findApplicableRule(
                        orgId(),
                        categoryId,
                        request.countryCode(),
                        normalizeState(request.stateCode()),
                        request.effectiveDate())
                .orElseThrow();
    }

    public boolean isInterState(String organizationStateCode, String placeOfSupplyStateCode) {
        return !isIntraState(organizationStateCode, placeOfSupplyStateCode);
    }

    public boolean isIntraState(String organizationStateCode, String placeOfSupplyStateCode) {
        return normalizeState(organizationStateCode).equalsIgnoreCase(normalizeState(placeOfSupplyStateCode));
    }

    TaxResult calculateWithRule(TaxCalculationRequest request, TaxCategory category, TaxRule rule) {
        TaxProviderCode providerCode = resolveProviderCode();
        TaxProvider provider = providerRegistry.resolve(providerCode);
        boolean intra = isIntraState(request.organizationStateCode(), request.placeOfSupplyStateCode());
        TaxCalculationContext context = new TaxCalculationContext(
                TaxProviderContext.of(providerCode, loadSettings(), category, rule),
                request.organizationStateCode(),
                request.placeOfSupplyStateCode(),
                request.quantity(),
                request.rate(),
                request.discount(),
                PricingMode.from(request.taxInclusive()),
                TaxType.from(request.taxType()),
                SplitStrategy.from(request.splitStrategy()),
                request.cgstSharePercent(),
                request.sgstSharePercent(),
                !intra,
                intra);
        return provider.calculate(context);
    }

    private OrganizationTaxSettings loadSettings() {
        if (settingsRepository == null) {
            return null;
        }
        return TenantContext.organizationId()
                .flatMap(settingsRepository::findById)
                .orElse(null);
    }

    private TaxProviderCode resolveProviderCode() {
        if (settingsRepository == null) {
            return TaxProviderCode.IndiaGST;
        }
        return TenantContext.organizationId()
                .flatMap(settingsRepository::findById)
                .map(OrganizationTaxSettings::getProviderCode)
                .orElse(TaxProviderCode.IndiaGST);
    }

    private static String normalizeState(String code) {
        return code == null ? "" : code.trim();
    }

    private static BigDecimal nullSafe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
