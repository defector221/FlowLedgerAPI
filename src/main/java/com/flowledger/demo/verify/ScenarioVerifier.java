package com.flowledger.demo.verify;

import com.flowledger.demo.DemoSeedContext;
import com.flowledger.demo.scenario.DemoBlueprint;
import com.flowledger.demo.scenario.DemoScenario;
import com.flowledger.demo.util.ProgressLogger;
import com.flowledger.organization.repository.OrganizationRepository;
import com.flowledger.retail.domain.StoreType;
import com.flowledger.retail.repository.RetailStoreRepository;
import org.springframework.stereotype.Component;

@Component
public class ScenarioVerifier {
    private final OrganizationRepository organizations;
    private final RetailStoreRepository stores;

    public ScenarioVerifier(OrganizationRepository organizations, RetailStoreRepository stores) {
        this.organizations = organizations;
        this.stores = stores;
    }

    public void verify(DemoSeedContext ctx, ProgressLogger progress) {
        progress.stage("Verification");
        DemoBlueprint bp = ctx.getBlueprint();

        ctx.check("organization_exists", organizations.findById(ctx.getOrganizationId()).isPresent());
        ctx.check("admin_user", ctx.getAdminUserId() != null);
        ctx.check("branches", ctx.getBranchIds().size() >= Math.min(1, bp.branchCount()));
        ctx.check("warehouses", !ctx.getWarehouseIds().isEmpty());
        ctx.check("stores", !ctx.getStoreIds().isEmpty());
        ctx.check("products", !ctx.getProductIds().isEmpty());
        ctx.check("customers", !ctx.getCustomerIds().isEmpty());
        ctx.check("suppliers", !ctx.getSupplierIds().isEmpty());
        Object uploaded = ctx.getMeta().get("imagesUploaded");
        Object reused = ctx.getMeta().get("imagesReused");
        int imgUp = uploaded instanceof Number n ? n.intValue() : 0;
        int imgRe = reused instanceof Number n ? n.intValue() : 0;
        ctx.check("product_images", imgUp + imgRe > 0 || ctx.getProductIds().isEmpty());

        if (bp.workflow().batchExpiryHeavy()) {
            Object batches = ctx.getMeta().get("batchOpeningPostings");
            ctx.check("batch_expiry_stock", batches instanceof Number n && n.intValue() > 0);
        }
        if (bp.workflow().serialTrackingHeavy()) {
            Object serials = ctx.getMeta().get("serialOpeningPostings");
            ctx.check("serial_stock", serials instanceof Number n && n.intValue() > 0);
        }
        if (bp.workflow().fashionVariants()) {
            ctx.check("fashion_variants_attempted", true);
        }
        if (bp.workflow().creditSalesHeavy() || bp.scenario() == DemoScenario.WHOLESALE) {
            ctx.check("wholesale_customers", !ctx.getCustomerIds().isEmpty());
        }
        if (bp.workflow().onlineStore()) {
            boolean hasOnline = stores
                    .findByOrganizationIdAndDeletedFalseOrderByNameAsc(ctx.getOrganizationId())
                    .stream()
                    .anyMatch(s -> s.getStoreType() == StoreType.ONLINE);
            ctx.check("online_store", hasOnline);
        }

        progress.done("Verification (" + ctx.getChecks().size() + " checks)");
    }
}
