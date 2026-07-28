package com.flowledger.migration.writer;

import static com.flowledger.migration.writer.WriterSupport.*;

import com.flowledger.customer.dto.CustomerDtos.Create;
import com.flowledger.customer.repository.CustomerRepository;
import com.flowledger.customer.service.CustomerService;
import com.flowledger.migration.domain.ImportModule;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class CustomerModuleWriter implements ModuleWriter {
    private final CustomerService customers;
    private final CustomerRepository repo;

    public CustomerModuleWriter(CustomerService customers, CustomerRepository repo) {
        this.customers = customers;
        this.repo = repo;
    }

    @Override
    public ImportModule module() {
        return ImportModule.CUSTOMER;
    }

    @Override
    public WriteResult write(UUID organizationId, Map<String, String> row) {
        String code = str(row, "customerCode");
        if (code != null && repo.existsByOrganizationIdAndCustomerCode(organizationId, code.toUpperCase(Locale.ROOT))) {
            return WriteResult.skipped("Customer already exists: " + code);
        }
        var created = customers.create(new Create(
                code == null ? null : code.toUpperCase(Locale.ROOT),
                required(row, "customerName"),
                str(row, "companyName"),
                str(row, "gstin"),
                str(row, "pan"),
                str(row, "email"),
                str(row, "phone"),
                str(row, "billingAddress"),
                null,
                str(row, "city"),
                str(row, "state"),
                null,
                null,
                null,
                str(row, "paymentTerms"),
                null,
                null));
        return WriteResult.imported(created.id(), "CUSTOMER");
    }
}
