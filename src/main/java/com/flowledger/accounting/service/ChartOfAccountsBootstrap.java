package com.flowledger.accounting.service;

import com.flowledger.accounting.entity.FiscalYear;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @deprecated Prefer {@link ChartOfAccountsBootstrapService}. Retained for existing injection sites.
 */
@Service
@Deprecated
public class ChartOfAccountsBootstrap {
    private final ChartOfAccountsBootstrapService bootstrapService;

    public ChartOfAccountsBootstrap(ChartOfAccountsBootstrapService bootstrapService) {
        this.bootstrapService = bootstrapService;
    }

    @Transactional
    public void initializeOrganization(UUID orgId, String financialYearStart) {
        bootstrapService.bootstrapOrganization(orgId, financialYearStart);
    }

    public FiscalYear ensureCurrentFiscalYear(UUID orgId, String financialYearStart) {
        return bootstrapService.ensureCurrentFiscalYear(orgId, financialYearStart);
    }
}
