package com.flowledger.tax.service;

import com.flowledger.common.service.OrganizationScopedService;
import com.flowledger.common.tenant.TenantContext;
import com.flowledger.tax.entity.TaxRule;
import com.flowledger.tax.entity.TaxRuleAuditLog;
import com.flowledger.tax.repository.TaxRuleAuditLogRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TaxAuditService extends OrganizationScopedService {
    private final TaxRuleAuditLogRepository auditLogRepository;

    public TaxAuditService(TaxRuleAuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void logPublish(UUID taxCategoryId, TaxRule previousRule, TaxRule newRule, String operation) {
        TaxRuleAuditLog log = new TaxRuleAuditLog();
        log.setOrganizationId(orgId());
        log.setTaxCategoryId(taxCategoryId);
        log.setPreviousRuleId(previousRule == null ? null : previousRule.getId());
        log.setNewRuleId(newRule.getId());
        log.setOperation(operation);
        log.setEffectiveFrom(newRule.getEffectiveFrom());
        log.setEffectiveTo(newRule.getEffectiveTo());
        log.setCreatedBy(TenantContext.userId().orElse(null));
        auditLogRepository.save(log);
    }

    @Transactional(readOnly = true)
    public List<TaxRuleAuditLog> listAudit(UUID taxCategoryId) {
        if (taxCategoryId == null) {
            return auditLogRepository.findByOrganizationIdOrderByCreatedAtDesc(orgId());
        }
        return auditLogRepository.findByOrganizationIdAndTaxCategoryIdOrderByCreatedAtDesc(orgId(), taxCategoryId);
    }

    @Transactional(readOnly = true)
    public TaxRuleAuditLog getAudit(UUID id) {
        return required(auditLogRepository.findByIdAndOrganizationId(id, orgId()), "Tax audit log");
    }
}
