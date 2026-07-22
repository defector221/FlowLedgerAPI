package com.flowledger.accounting.service;

import com.flowledger.accounting.dto.AccountingDtos.LedgerLineResponse;
import com.flowledger.accounting.dto.LedgerLineView;
import com.flowledger.accounting.repository.AccountRepository;
import com.flowledger.accounting.repository.JournalEntryLineRepository;
import com.flowledger.accounting.util.AccountingMoney;
import com.flowledger.common.exception.ResourceNotFoundException;
import com.flowledger.common.tenant.TenantContext;
import com.flowledger.customer.repository.CustomerRepository;
import com.flowledger.supplier.repository.SupplierRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerService {
    private final JournalEntryLineRepository lines;
    private final AccountRepository accounts;
    private final CustomerRepository customers;
    private final SupplierRepository suppliers;

    public LedgerService(
            JournalEntryLineRepository lines,
            AccountRepository accounts,
            CustomerRepository customers,
            SupplierRepository suppliers) {
        this.lines = lines;
        this.accounts = accounts;
        this.customers = customers;
        this.suppliers = suppliers;
    }

    @Transactional(readOnly = true)
    public List<LedgerLineResponse> accountLedger(UUID accountId, LocalDate from, LocalDate to) {
        UUID org = TenantContext.getOrganizationId();
        validateDateRange(from, to);
        accounts.findByIdAndOrganizationId(accountId, org)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        return build(lines.findPostedLedgerForAccount(org, accountId, from, to));
    }

    @Transactional(readOnly = true)
    public List<LedgerLineResponse> customerLedger(UUID customerId, LocalDate from, LocalDate to) {
        UUID org = TenantContext.getOrganizationId();
        validateDateRange(from, to);
        customers
                .findByIdAndOrganizationId(customerId, org)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        return build(lines.findPostedLedgerForCustomer(org, customerId, from, to));
    }

    @Transactional(readOnly = true)
    public List<LedgerLineResponse> supplierLedger(UUID supplierId, LocalDate from, LocalDate to) {
        UUID org = TenantContext.getOrganizationId();
        validateDateRange(from, to);
        suppliers
                .findByIdAndOrganizationId(supplierId, org)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found"));
        return build(lines.findPostedLedgerForSupplier(org, supplierId, from, to));
    }

    private List<LedgerLineResponse> build(List<LedgerLineView> journalLines) {
        List<LedgerLineResponse> result = new ArrayList<>(journalLines.size());
        BigDecimal running = AccountingMoney.zero();
        for (LedgerLineView line : journalLines) {
            running = running.add(AccountingMoney.normalize(line.debitAmount()))
                    .subtract(AccountingMoney.normalize(line.creditAmount()));
            result.add(new LedgerLineResponse(
                    line.journalEntryId(),
                    line.entryNumber(),
                    line.entryDate(),
                    line.description(),
                    line.debitAmount(),
                    line.creditAmount(),
                    running,
                    line.accountId(),
                    line.accountCode(),
                    line.accountName(),
                    line.customerId(),
                    line.supplierId()));
        }
        return result;
    }

    static void validateDateRange(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("'from' date must be on or before 'to' date");
        }
    }
}
