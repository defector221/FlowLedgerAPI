package com.flowledger.tax.service;

import com.flowledger.common.service.OrganizationScopedService;
import com.flowledger.common.tenant.TenantContext;
import com.flowledger.tax.domain.JurisdictionType;
import com.flowledger.tax.domain.TaxProviderCode;
import com.flowledger.tax.dto.TaxAdminDtos.*;
import com.flowledger.tax.dto.TaxCalculationDtos.*;
import com.flowledger.tax.entity.*;
import com.flowledger.tax.repository.*;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class TaxAdminService extends OrganizationScopedService {
    private final TaxCategoryRepository categoryRepository;
    private final TaxRuleRepository ruleRepository;
    private final TaxJurisdictionRepository jurisdictionRepository;
    private final HsnSacCodeRepository hsnSacCodeRepository;
    private final OrganizationTaxSettingsRepository settingsRepository;
    private final TaxAuditService auditService;
    private final TaxCalculationService calculationService;

    public TaxAdminService(
            TaxCategoryRepository categoryRepository,
            TaxRuleRepository ruleRepository,
            TaxJurisdictionRepository jurisdictionRepository,
            HsnSacCodeRepository hsnSacCodeRepository,
            OrganizationTaxSettingsRepository settingsRepository,
            TaxAuditService auditService,
            TaxCalculationService calculationService) {
        this.categoryRepository = categoryRepository;
        this.ruleRepository = ruleRepository;
        this.jurisdictionRepository = jurisdictionRepository;
        this.hsnSacCodeRepository = hsnSacCodeRepository;
        this.settingsRepository = settingsRepository;
        this.auditService = auditService;
        this.calculationService = calculationService;
    }

    public CategoryResponse createCategory(CategoryRequest request) {
        if (categoryRepository.existsByOrganizationIdAndCodeIgnoreCase(orgId(), request.code(), null)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tax category code already exists");
        }
        TaxCategory category = new TaxCategory();
        category.setOrganizationId(orgId());
        category.setCode(request.code().trim());
        category.setName(request.name().trim());
        category.setDescription(request.description());
        category.setCountryCode(request.countryCode().trim().toUpperCase());
        if (request.active() != null) {
            category.setActive(request.active());
        }
        return toCategoryResponse(categoryRepository.save(category));
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> listCategories() {
        return categoryRepository.findByOrganizationId(orgId()).stream()
                .map(this::toCategoryResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse getCategory(UUID id) {
        return toCategoryResponse(loadCategory(id));
    }

    public CategoryResponse updateCategory(UUID id, CategoryRequest request) {
        TaxCategory category = loadCategory(id);
        if (category.isSystemDefined()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "System-defined tax category cannot be modified");
        }
        if (categoryRepository.existsByOrganizationIdAndCodeIgnoreCase(orgId(), request.code(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tax category code already exists");
        }
        category.setCode(request.code().trim());
        category.setName(request.name().trim());
        category.setDescription(request.description());
        category.setCountryCode(request.countryCode().trim().toUpperCase());
        if (request.active() != null) {
            category.setActive(request.active());
        }
        return toCategoryResponse(categoryRepository.save(category));
    }

    public RuleResponse createRuleVersion(RuleRequest request) {
        TaxCategory category = loadCategory(request.taxCategoryId());
        LocalDate effectiveFrom = request.effectiveFrom();
        UUID org = orgId();

        TaxRule rule = new TaxRule();
        rule.setOrganizationId(org);
        rule.setTaxCategoryId(category.getId());
        rule.setTaxType(request.taxType() == null ? com.flowledger.product.entity.TaxType.GST : request.taxType());
        rule.setCountryCode(request.countryCode().trim().toUpperCase());
        rule.setStateCode(request.stateCode());
        rule.setCgstRate(request.cgstRate());
        rule.setSgstRate(request.sgstRate());
        rule.setIgstRate(request.igstRate());
        rule.setCessRate(request.cessRate() == null ? java.math.BigDecimal.ZERO : request.cessRate());
        rule.setEffectiveFrom(effectiveFrom);
        rule.setEffectiveTo(request.effectiveTo());
        rule.setInclusive(Boolean.TRUE.equals(request.inclusive()));
        rule.setPriority(request.priority() == null ? 0 : request.priority());
        rule.setReverseCharge(Boolean.TRUE.equals(request.reverseCharge()));
        rule.setZeroRated(Boolean.TRUE.equals(request.zeroRated()));
        rule.setExempt(Boolean.TRUE.equals(request.exempt()));
        rule.setNilRated(Boolean.TRUE.equals(request.nilRated()));
        rule.setVersionLabel(request.versionLabel());
        rule.setPublishedBy(TenantContext.userId().orElse(null));
        TaxRule saved = ruleRepository.save(rule);

        ruleRepository
                .findByOrganizationIdAndTaxCategoryIdAndActiveTrueOrderByEffectiveFromDesc(org, category.getId())
                .stream()
                .filter(r -> !r.getId().equals(saved.getId()))
                .filter(r -> r.getSupersededBy() == null)
                .filter(r -> r.getCountryCode().equalsIgnoreCase(request.countryCode()))
                .filter(r -> sameState(r.getStateCode(), request.stateCode()))
                .forEach(previous -> {
                    previous.setSupersededBy(saved.getId());
                    previous.setActive(false);
                    if (previous.getEffectiveTo() == null
                            || previous.getEffectiveTo().isAfter(effectiveFrom)) {
                        previous.setEffectiveTo(effectiveFrom.minusDays(1));
                    }
                    ruleRepository.save(previous);
                    auditService.logPublish(category.getId(), previous, saved, "SUPERSEDE");
                });

        auditService.logPublish(category.getId(), null, saved, "PUBLISH");
        return toRuleResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<RuleResponse> listRules(UUID categoryId) {
        return ruleRepository.findByOrganizationIdAndTaxCategoryIdOrderByEffectiveFromDesc(orgId(), categoryId).stream()
                .map(this::toRuleResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public RuleResponse getRule(UUID id) {
        return toRuleResponse(loadRule(id));
    }

    public JurisdictionResponse createJurisdiction(JurisdictionRequest request) {
        TaxJurisdiction jurisdiction = new TaxJurisdiction();
        jurisdiction.setOrganizationId(orgId());
        jurisdiction.setCountry(request.country().trim().toUpperCase());
        jurisdiction.setState(request.state());
        jurisdiction.setCode(request.code().trim());
        jurisdiction.setType(request.type());
        if (request.active() != null) {
            jurisdiction.setActive(request.active());
        }
        return toJurisdictionResponse(jurisdictionRepository.save(jurisdiction));
    }

    @Transactional(readOnly = true)
    public List<JurisdictionResponse> listJurisdictions() {
        return jurisdictionRepository.findByOrganizationId(orgId()).stream()
                .map(this::toJurisdictionResponse)
                .toList();
    }

    public JurisdictionResponse updateJurisdiction(UUID id, JurisdictionRequest request) {
        TaxJurisdiction jurisdiction = loadJurisdiction(id);
        jurisdiction.setCountry(request.country().trim().toUpperCase());
        jurisdiction.setState(request.state());
        jurisdiction.setCode(request.code().trim());
        jurisdiction.setType(request.type());
        if (request.active() != null) {
            jurisdiction.setActive(request.active());
        }
        return toJurisdictionResponse(jurisdictionRepository.save(jurisdiction));
    }

    public HsnSacResponse createHsnSac(HsnSacRequest request) {
        HsnSacCode code = new HsnSacCode();
        code.setOrganizationId(orgId());
        code.setCode(request.code().trim());
        code.setDescription(request.description());
        code.setType(request.type().trim().toUpperCase());
        if (request.active() != null) {
            code.setActive(request.active());
        }
        return toHsnSacResponse(hsnSacCodeRepository.save(code));
    }

    @Transactional(readOnly = true)
    public List<HsnSacResponse> listHsnSac() {
        return hsnSacCodeRepository.findByOrganizationId(orgId()).stream()
                .map(this::toHsnSacResponse)
                .toList();
    }

    public HsnSacResponse updateHsnSac(UUID id, HsnSacRequest request) {
        HsnSacCode code = loadHsnSac(id);
        code.setCode(request.code().trim());
        code.setDescription(request.description());
        code.setType(request.type().trim().toUpperCase());
        if (request.active() != null) {
            code.setActive(request.active());
        }
        return toHsnSacResponse(hsnSacCodeRepository.save(code));
    }

    public SettingsResponse upsertSettings(SettingsRequest request) {
        OrganizationTaxSettings settings = settingsRepository.findById(orgId()).orElseGet(() -> {
            OrganizationTaxSettings created = new OrganizationTaxSettings();
            created.setOrganizationId(orgId());
            return created;
        });
        if (request.defaultTaxCategoryId() != null) {
            loadCategory(request.defaultTaxCategoryId());
            settings.setDefaultTaxCategoryId(request.defaultTaxCategoryId());
        }
        if (request.providerCode() != null) {
            settings.setProviderCode(request.providerCode());
        }
        if (request.roundingScale() != null) {
            settings.setRoundingScale(request.roundingScale());
        }
        if (request.roundingMode() != null) {
            settings.setRoundingMode(request.roundingMode());
        }
        return toSettingsResponse(settingsRepository.save(settings));
    }

    @Transactional(readOnly = true)
    public SettingsResponse getSettings() {
        return settingsRepository
                .findById(orgId())
                .map(this::toSettingsResponse)
                .orElseGet(() -> new SettingsResponse(orgId(), null, TaxProviderCode.IndiaGST, 2, "HALF_UP"));
    }

    @Transactional(readOnly = true)
    public TaxResult simulate(SimulateRequest request) {
        LocalDate date = request.effectiveDate() == null ? LocalDate.now() : request.effectiveDate();
        return calculationService.calculateLineTax(
                request.line(),
                request.organizationStateCode(),
                request.placeOfSupplyStateCode(),
                request.countryCode(),
                date);
    }

    private static boolean sameState(String left, String right) {
        String a = left == null ? "" : left.trim();
        String b = right == null ? "" : right.trim();
        return a.equalsIgnoreCase(b);
    }

    private TaxCategory loadCategory(UUID id) {
        return required(categoryRepository.findByIdAndOrganizationId(id, orgId()), "Tax category");
    }

    private TaxRule loadRule(UUID id) {
        return required(ruleRepository.findByIdAndOrganizationId(id, orgId()), "Tax rule");
    }

    private TaxJurisdiction loadJurisdiction(UUID id) {
        return required(jurisdictionRepository.findByIdAndOrganizationId(id, orgId()), "Tax jurisdiction");
    }

    private HsnSacCode loadHsnSac(UUID id) {
        return required(hsnSacCodeRepository.findByIdAndOrganizationId(id, orgId()), "HSN/SAC code");
    }

    private CategoryResponse toCategoryResponse(TaxCategory category) {
        return new CategoryResponse(
                category.getId(),
                category.getCode(),
                category.getName(),
                category.getDescription(),
                category.getCountryCode(),
                category.isActive(),
                category.isSystemDefined());
    }

    private RuleResponse toRuleResponse(TaxRule rule) {
        return new RuleResponse(
                rule.getId(),
                rule.getTaxCategoryId(),
                rule.getTaxType(),
                rule.getCountryCode(),
                rule.getStateCode(),
                rule.getCgstRate(),
                rule.getSgstRate(),
                rule.getIgstRate(),
                rule.getCessRate(),
                rule.getEffectiveFrom(),
                rule.getEffectiveTo(),
                rule.isInclusive(),
                rule.getPriority(),
                rule.isReverseCharge(),
                rule.isZeroRated(),
                rule.isExempt(),
                rule.isNilRated(),
                rule.isActive(),
                rule.getVersionLabel(),
                rule.getSupersededBy());
    }

    private JurisdictionResponse toJurisdictionResponse(TaxJurisdiction jurisdiction) {
        return new JurisdictionResponse(
                jurisdiction.getId(),
                jurisdiction.getCountry(),
                jurisdiction.getState(),
                jurisdiction.getCode(),
                jurisdiction.getType() == null ? JurisdictionType.GST : jurisdiction.getType(),
                jurisdiction.isActive());
    }

    private HsnSacResponse toHsnSacResponse(HsnSacCode code) {
        return new HsnSacResponse(code.getId(), code.getCode(), code.getDescription(), code.getType(), code.isActive());
    }

    private SettingsResponse toSettingsResponse(OrganizationTaxSettings settings) {
        return new SettingsResponse(
                settings.getOrganizationId(),
                settings.getDefaultTaxCategoryId(),
                settings.getProviderCode(),
                settings.getRoundingScale(),
                settings.getRoundingMode());
    }
}
