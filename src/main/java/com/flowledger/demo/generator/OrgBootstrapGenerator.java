package com.flowledger.demo.generator;

import com.flowledger.accounting.service.ChartOfAccountsBootstrapService;
import com.flowledger.common.tenant.TenantContext;
import com.flowledger.demo.DemoSeedContext;
import com.flowledger.demo.scenario.DemoBlueprint;
import com.flowledger.demo.util.ProgressLogger;
import com.flowledger.organization.entity.Organization;
import com.flowledger.organization.entity.OrganizationSettings;
import com.flowledger.organization.repository.OrganizationRepository;
import com.flowledger.organization.repository.OrganizationSettingsRepository;
import com.flowledger.platform.domain.ModuleCodes;
import com.flowledger.platform.service.EditionService;
import com.flowledger.platform.service.OrganizationModuleService;
import com.flowledger.subscription.service.SubscriptionService;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OrgBootstrapGenerator {
    private static final List<UserGenerator.DemoUserSpec> EXTRA_USERS = List.of(
            new UserGenerator.DemoUserSpec("branch.manager", "Branch", "Manager", "BRANCH_MANAGER"),
            new UserGenerator.DemoUserSpec("store.manager", "Store", "Manager", "RETAIL_STORE_MANAGER"),
            new UserGenerator.DemoUserSpec("cashier", "Retail", "Cashier", "RETAIL_CASHIER"),
            new UserGenerator.DemoUserSpec("warehouse", "Warehouse", "Manager", "WAREHOUSE_MANAGER"),
            new UserGenerator.DemoUserSpec("sales", "Sales", "Manager", "SALES_MANAGER"),
            new UserGenerator.DemoUserSpec("viewer", "Read", "Only", "VIEWER"));

    private final OrganizationRepository organizations;
    private final OrganizationSettingsRepository settings;
    private final ChartOfAccountsBootstrapService accounting;
    private final OrganizationModuleService organizationModuleService;
    private final EditionService editions;
    private final SubscriptionService subscriptions;
    private final UserGenerator userGenerator;

    public OrgBootstrapGenerator(
            OrganizationRepository organizations,
            OrganizationSettingsRepository settings,
            ChartOfAccountsBootstrapService accounting,
            OrganizationModuleService organizationModuleService,
            EditionService editions,
            SubscriptionService subscriptions,
            UserGenerator userGenerator) {
        this.organizations = organizations;
        this.settings = settings;
        this.accounting = accounting;
        this.organizationModuleService = organizationModuleService;
        this.editions = editions;
        this.subscriptions = subscriptions;
        this.userGenerator = userGenerator;
    }

    @Transactional
    public void generate(DemoSeedContext ctx, ProgressLogger progress) {
        DemoBlueprint blueprint = ctx.getBlueprint();
        progress.stage("Bootstrapping organization");

        Organization org = new Organization();
        org.setName(blueprint.organizationName());
        org.setEmail("contact@" + ctx.getScenario().slug() + ".demo");
        org.setCountry("India");
        org.setCurrency("INR");
        org.setFinancialYearStart("04-01");
        org.setInvoicePrefix(ctx.getScenario().slug().substring(0, Math.min(4, ctx.getScenario().slug().length()))
                .toUpperCase(Locale.ROOT));
        org.setInvoiceNumberFormat("{PREFIX}/{FY}/{SEQ:6}");
        org.setOnboardingCompleted(true);
        org.setAllowNegativeStock(true);
        org = organizations.save(org);

        OrganizationSettings orgSettings = new OrganizationSettings();
        orgSettings.setOrganizationId(org.getId());
        settings.save(orgSettings);

        UUID orgId = org.getId();
        ctx.setOrganizationId(orgId);
        TenantContext.set(orgId, null);

        UUID adminId = userGenerator
                .createOrgUser(
                        ctx,
                        new UserGenerator.DemoUserSpec("admin", "Demo", "Admin", "ORGANIZATION_ADMIN"),
                        progress,
                        true)
                .orElseThrow(() -> new IllegalStateException("ORGANIZATION_ADMIN role missing"));
        ctx.setAdminUserId(adminId);
        TenantContext.set(orgId, adminId);

        accounting.bootstrapOrganization(orgId, org.getFinancialYearStart());

        for (UserGenerator.DemoUserSpec spec : EXTRA_USERS) {
            userGenerator.createOrgUser(ctx, spec, progress, false);
        }

        editions.provisionNewOrganization(orgId, "FREE", adminId);
        subscriptions.ensureDefaultSubscription(adminId, "FREE");
        subscriptions.ensureOrganizationSubscription(orgId, "FREE");

        // Dependencies before RETAIL (module graph: RETAIL → INVENTORY, ACCOUNTING)
        organizationModuleService.setModuleEnabled(orgId, ModuleCodes.INVENTORY, true, adminId);
        organizationModuleService.setModuleEnabled(orgId, ModuleCodes.ACCOUNTING, true, adminId);
        organizationModuleService.setModuleEnabled(orgId, ModuleCodes.RETAIL, true, adminId);

        progress.done("Organization bootstrap (" + org.getName() + ")");
    }
}
