package com.flowledger.migration.writer;

import static com.flowledger.migration.writer.WriterSupport.*;

import com.flowledger.migration.domain.ImportModule;
import com.flowledger.supplier.dto.SupplierDtos.Create;
import com.flowledger.supplier.repository.SupplierRepository;
import com.flowledger.supplier.service.SupplierService;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class SupplierModuleWriter implements ModuleWriter {
    private final SupplierService suppliers;
    private final SupplierRepository repo;

    public SupplierModuleWriter(SupplierService suppliers, SupplierRepository repo) {
        this.suppliers = suppliers;
        this.repo = repo;
    }

    @Override
    public ImportModule module() {
        return ImportModule.SUPPLIER;
    }

    @Override
    public WriteResult write(UUID organizationId, Map<String, String> row) {
        String code = str(row, "supplierCode");
        if (code != null && repo.existsByOrganizationIdAndSupplierCode(organizationId, code.toUpperCase(Locale.ROOT))) {
            return WriteResult.skipped("Supplier already exists: " + code);
        }
        var created = suppliers.create(new Create(
                code == null ? null : code.toUpperCase(Locale.ROOT),
                required(row, "supplierName"),
                str(row, "companyName"),
                str(row, "gstin"),
                str(row, "pan"),
                str(row, "email"),
                str(row, "phone"),
                str(row, "address"),
                null,
                str(row, "city"),
                str(row, "state"),
                null,
                null,
                str(row, "paymentTerms"),
                null,
                null,
                null,
                null,
                null));
        return WriteResult.imported(created.id(), "SUPPLIER");
    }
}
