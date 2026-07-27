package com.flowledger.migration.writer;

import static com.flowledger.migration.writer.WriterSupport.*;

import com.flowledger.customer.repository.CustomerRepository;
import com.flowledger.migration.domain.ImportModule;
import com.flowledger.payment.dto.PaymentDtos.PaymentRequest;
import com.flowledger.payment.entity.Payment;
import com.flowledger.payment.service.PaymentService;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ReceiptModuleWriter implements ModuleWriter {
    private final PaymentService payments;
    private final CustomerRepository customers;

    public ReceiptModuleWriter(PaymentService payments, CustomerRepository customers) {
        this.payments = payments;
        this.customers = customers;
    }

    @Override
    public ImportModule module() {
        return ImportModule.RECEIPT;
    }

    @Override
    public WriteResult write(UUID organizationId, Map<String, String> row) {
        UUID customerId = null;
        String code = str(row, "customerCode");
        if (code != null) {
            customerId = customers
                    .findByOrganizationIdAndCustomerCode(organizationId, code.toUpperCase(Locale.ROOT))
                    .map(c -> c.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + code));
        }
        var created = payments.create(new PaymentRequest(
                dateOrToday(row, "paymentDate"),
                Payment.Type.RECEIPT,
                Payment.Party.CUSTOMER,
                customerId,
                null,
                decimalOrZero(row, "amount"),
                str(row, "paymentMode") == null ? "CASH" : str(row, "paymentMode"),
                str(row, "reference"),
                null,
                "Migration import",
                List.of()));
        return WriteResult.imported(created.getId(), "RECEIPT");
    }
}
