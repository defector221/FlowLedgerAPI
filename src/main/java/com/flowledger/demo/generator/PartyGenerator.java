package com.flowledger.demo.generator;

import com.flowledger.common.tenant.TenantContext;
import com.flowledger.customer.dto.CustomerDtos;
import com.flowledger.customer.service.CustomerService;
import com.flowledger.demo.DemoSeedContext;
import com.flowledger.demo.scenario.DemoBlueprint;
import com.flowledger.demo.scenario.DemoScenario;
import com.flowledger.demo.util.DemoFaker;
import com.flowledger.demo.util.ProgressLogger;
import com.flowledger.supplier.dto.SupplierDtos;
import com.flowledger.supplier.service.SupplierService;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PartyGenerator {
    private static final List<String> CUSTOMER_NOTES = List.of("Walk-in", "Retail", "Wholesale", "VIP");

    private final CustomerService customerService;
    private final SupplierService supplierService;
    private final DemoFaker faker;

    public PartyGenerator(CustomerService customerService, SupplierService supplierService, DemoFaker faker) {
        this.customerService = customerService;
        this.supplierService = supplierService;
        this.faker = faker;
    }

    @Transactional
    public void generate(DemoSeedContext ctx, ProgressLogger progress) {
        DemoBlueprint blueprint = ctx.getBlueprint();
        TenantContext.set(ctx.getOrganizationId(), ctx.getAdminUserId());
        progress.stage("Creating customers and suppliers");

        boolean wholesale = blueprint.scenario() == DemoScenario.WHOLESALE
                || blueprint.workflow().creditSalesHeavy();
        int customerCount = blueprint.customerCount();
        for (int i = 1; i <= customerCount; i++) {
            String note = wholesale ? "Wholesale" : CUSTOMER_NOTES.get((i - 1) % CUSTOMER_NOTES.size());
            String name = note + " Customer " + i;
            boolean withGst = i % 3 == 0;
            String state = "Karnataka";
            String stateCode = "29";
            if (i % 4 == 0) {
                state = "Maharashtra";
                stateCode = "27";
            } else if (i % 5 == 0) {
                state = "Tamil Nadu";
                stateCode = "33";
            }
            var created = customerService.create(new CustomerDtos.Create(
                    null,
                    name,
                    i % 5 == 0 ? name + " Pvt Ltd" : null,
                    withGst ? faker.gstin() : null,
                    null,
                    name.replace(" ", ".").toLowerCase() + "@"
                            + ctx.getScenario().slug() + ".demo",
                    faker.phone(),
                    faker.faker().address().streetAddress(),
                    null,
                    faker.faker().address().city(),
                    state,
                    stateCode,
                    "India",
                    wholesale ? new BigDecimal("500000") : BigDecimal.valueOf(10_000 + i * 100L),
                    wholesale ? "NET30" : "COD",
                    null,
                    note));
            ctx.getCustomerIds().add(created.id());
            if (i % Math.max(1, customerCount / 10) == 0 || i == customerCount) {
                progress.progress("Customers", i, customerCount);
            }
        }

        int supplierCount = blueprint.supplierCount();
        for (int i = 1; i <= supplierCount; i++) {
            String name = "Supplier " + i;
            var created = supplierService.create(new SupplierDtos.Create(
                    null,
                    name,
                    name + " Traders",
                    i % 2 == 0 ? faker.gstin() : null,
                    null,
                    "supplier" + i + "@" + ctx.getScenario().slug() + ".demo",
                    faker.phone(),
                    faker.faker().address().streetAddress(),
                    null,
                    faker.faker().address().city(),
                    "Maharashtra",
                    "27",
                    "India",
                    "NET15",
                    null,
                    "Demo Bank",
                    faker.faker().number().digits(12),
                    "HDFC000" + faker.faker().number().digits(4),
                    "Demo supplier"));
            ctx.getSupplierIds().add(created.id());
            if (i % Math.max(1, supplierCount / 5) == 0 || i == supplierCount) {
                progress.progress("Suppliers", i, supplierCount);
            }
        }

        progress.done(String.format(
                "Parties (%d customers, %d suppliers)",
                ctx.getCustomerIds().size(), ctx.getSupplierIds().size()));
    }
}
