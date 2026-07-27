package com.flowledger.migration.writer;

import static com.flowledger.migration.writer.WriterSupport.*;

import com.flowledger.migration.domain.ImportModule;
import com.flowledger.payment.dto.PaymentDtos.PaymentRequest;
import com.flowledger.payment.entity.Payment;
import com.flowledger.payment.service.PaymentService;
import com.flowledger.supplier.repository.SupplierRepository;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PaymentModuleWriter implements ModuleWriter {
    private final PaymentService payments;
    private final SupplierRepository suppliers;

    public PaymentModuleWriter(PaymentService payments, SupplierRepository suppliers) {
        this.payments = payments;
        this.suppliers = suppliers;
    }

    @Override
    public ImportModule module() {
        return ImportModule.PAYMENT;
    }

    @Override
    public WriteResult write(UUID organizationId, Map<String, String> row) {
        UUID supplierId = null;
        String code = str(row, "supplierCode");
        if (code != null) {
            supplierId = suppliers
                    .findByOrganizationIdAndSupplierCode(organizationId, code.toUpperCase(Locale.ROOT))
                    .map(s -> s.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Supplier not found: " + code));
        }
        var created = payments.create(new PaymentRequest(
                dateOrToday(row, "paymentDate"),
                Payment.Type.PAYMENT,
                Payment.Party.SUPPLIER,
                null,
                supplierId,
                decimalOrZero(row, "amount"),
                str(row, "paymentMode") == null ? "CASH" : str(row, "paymentMode"),
                str(row, "reference"),
                null,
                "Migration import",
                List.of()));
        return WriteResult.imported(created.getId(), "PAYMENT");
    }
}
