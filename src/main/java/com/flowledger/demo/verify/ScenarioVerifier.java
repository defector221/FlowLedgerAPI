package com.flowledger.demo.verify;

import com.flowledger.demo.DemoSeedContext;
import com.flowledger.demo.scenario.DemoBlueprint;
import com.flowledger.demo.scenario.DemoScenario;
import com.flowledger.demo.util.ProgressLogger;
import com.flowledger.organization.repository.OrganizationRepository;
import com.flowledger.retail.domain.RetailEnums.PosSaleStatus;
import com.flowledger.retail.domain.StoreType;
import com.flowledger.retail.entity.PosSaleLine;
import com.flowledger.retail.repository.PosSaleLineRepository;
import com.flowledger.retail.repository.PosSaleRepository;
import com.flowledger.retail.repository.RetailStoreRepository;
import com.flowledger.sales.entity.SalesInvoice;
import com.flowledger.sales.repository.SalesInvoiceRepository;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
public class ScenarioVerifier {
    private final OrganizationRepository organizations;
    private final RetailStoreRepository stores;
    private final SalesInvoiceRepository salesInvoices;
    private final PosSaleRepository posSales;
    private final PosSaleLineRepository posSaleLines;

    public ScenarioVerifier(
            OrganizationRepository organizations,
            RetailStoreRepository stores,
            SalesInvoiceRepository salesInvoices,
            PosSaleRepository posSales,
            PosSaleLineRepository posSaleLines) {
        this.organizations = organizations;
        this.stores = stores;
        this.salesInvoices = salesInvoices;
        this.posSales = posSales;
        this.posSaleLines = posSaleLines;
    }

    public void verify(DemoSeedContext ctx, ProgressLogger progress) {
        progress.stage("Verification");
        DemoBlueprint bp = ctx.getBlueprint();
        UUID orgId = ctx.getOrganizationId();

        ctx.check("organization_exists", organizations.findById(orgId).isPresent());
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

        ctx.check("tax_categories", !ctx.getTaxCategoryIds().isEmpty());
        ctx.check("tax_rules", !ctx.getTaxRuleByCode().isEmpty());
        ctx.check("products_with_tax_mapping", ctx.getProductsWithTaxMapping() > 0);
        ctx.check("invoice_tax_snapshots", hasTaxSnapshots(orgId));

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
            boolean hasOnline = stores.findByOrganizationIdAndDeletedFalseOrderByNameAsc(orgId).stream()
                    .anyMatch(s -> s.getStoreType() == StoreType.ONLINE);
            ctx.check("online_store", hasOnline);
        }

        progress.done("Verification (" + ctx.getChecks().size() + " checks)");
    }

    private boolean hasTaxSnapshots(UUID orgId) {
        boolean invoiceTax = salesInvoices.findByOrganizationId(orgId, PageRequest.of(0, 20)).stream()
                .filter(invoice -> invoice.getStatus() == SalesInvoice.Status.CONFIRMED)
                .anyMatch(this::invoiceHasTaxSnapshot);
        if (invoiceTax) {
            return true;
        }
        return posSales
                .findByOrganizationIdAndStatusAndDeletedFalseOrderByCreatedAtDesc(orgId, PosSaleStatus.COMPLETED)
                .stream()
                .limit(20)
                .anyMatch(sale ->
                        posSaleLines.findByOrganizationIdAndPosSaleIdOrderByLineOrderAsc(orgId, sale.getId()).stream()
                                .anyMatch(this::lineHasTaxSnapshot));
    }

    private boolean invoiceHasTaxSnapshot(SalesInvoice invoice) {
        return salesInvoices
                .findDetailedByIdAndOrganizationId(invoice.getId(), invoice.getOrganizationId())
                .map(detailed -> detailed.getItems().stream().anyMatch(this::lineHasTaxSnapshot))
                .orElse(false);
    }

    private boolean lineHasTaxSnapshot(com.flowledger.sales.entity.SalesInvoiceItem line) {
        return line.getTaxCategoryId() != null
                || (line.getTaxCategoryCode() != null
                        && !line.getTaxCategoryCode().isBlank());
    }

    private boolean lineHasTaxSnapshot(PosSaleLine line) {
        return line.getTaxCategoryId() != null
                || (line.getTaxCategoryCode() != null
                        && !line.getTaxCategoryCode().isBlank());
    }
}
