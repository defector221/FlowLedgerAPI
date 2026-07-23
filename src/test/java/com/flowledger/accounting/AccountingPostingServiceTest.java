package com.flowledger.accounting.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.flowledger.accounting.domain.AccountingStatus;
import com.flowledger.accounting.domain.JournalSource;
import com.flowledger.accounting.domain.PeriodStatus;
import com.flowledger.accounting.domain.SystemAccountKey;
import com.flowledger.accounting.entity.Account;
import com.flowledger.accounting.entity.AccountingPeriod;
import com.flowledger.accounting.entity.JournalEntry;
import com.flowledger.accounting.repository.AccountRepository;
import com.flowledger.accounting.repository.AccountingPeriodRepository;
import com.flowledger.accounting.repository.FiscalYearRepository;
import com.flowledger.accounting.repository.JournalEntryLineRepository;
import com.flowledger.accounting.repository.JournalEntryRepository;
import com.flowledger.common.util.DocumentNumberService;
import com.flowledger.organization.entity.Organization;
import com.flowledger.organization.repository.OrganizationRepository;
import com.flowledger.sales.entity.SalesInvoice;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccountingPostingServiceTest {
    @Mock
    JournalEntryRepository journals;

    @Mock
    JournalEntryLineRepository lines;

    @Mock
    AccountRepository accounts;

    @Mock
    FiscalYearRepository fiscalYears;

    @Mock
    AccountingPeriodRepository periods;

    @Mock
    DocumentNumberService numbers;

    @Mock
    OrganizationRepository organizations;

    @Mock
    ChartOfAccountsBootstrapService bootstrap;

    JournalValidationService validation;
    AccountingPostingService posting;
    UUID orgId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        validation = new JournalValidationService(accounts, periods);
        posting = new AccountingPostingService(
                journals, lines, accounts, fiscalYears, validation, numbers, organizations, bootstrap);
        Organization org = new Organization();
        org.setId(orgId);
        org.setFinancialYearStart("04-01");
        when(organizations.findById(orgId)).thenReturn(Optional.of(org));
        when(accounts.existsByOrganizationIdAndSystemAccountKeyIsNotNull(orgId)).thenReturn(true);
        when(journals.findByOrganizationIdAndSourceAndReferenceId(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(numbers.next(any(), any(), any(), any(), any(), any())).thenReturn("JV/2026-27/000001");
        AccountingPeriod period = new AccountingPeriod();
        period.setId(UUID.randomUUID());
        period.setFiscalYearId(UUID.randomUUID());
        period.setStatus(PeriodStatus.OPEN);
        period.setName("Jul");
        when(periods.findCovering(eq(orgId), any())).thenReturn(Optional.of(period));
        for (SystemAccountKey key : SystemAccountKey.values()) {
            Account account = new Account();
            account.setId(UUID.randomUUID());
            account.setOrganizationId(orgId);
            account.setSystemAccountKey(key);
            account.setActive(true);
            account.setAllowManualPosting(true);
            account.setAccountName(key.name());
            when(accounts.findByOrganizationIdAndSystemAccountKey(orgId, key)).thenReturn(Optional.of(account));
            when(accounts.findByIdAndOrganizationId(account.getId(), orgId)).thenReturn(Optional.of(account));
            when(accounts.findById(account.getId())).thenReturn(Optional.of(account));
        }
        when(journals.save(any(JournalEntry.class))).thenAnswer((Answer<JournalEntry>) invocation -> {
            JournalEntry journalEntry = invocation.getArgument(0);
            if (journalEntry.getId() == null) {
                journalEntry.setId(UUID.randomUUID());
            }
            return journalEntry;
        });
        when(lines.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void postsSalesInvoiceWithIgstIdempotently() {
        SalesInvoice invoice = new SalesInvoice();
        invoice.setId(UUID.randomUUID());
        invoice.setOrganizationId(orgId);
        invoice.setCustomerId(UUID.randomUUID());
        invoice.setInvoiceNumber("INV-1");
        invoice.setInvoiceDate(LocalDate.of(2026, 7, 1));
        invoice.setTaxableAmount(new BigDecimal("1000.00"));
        invoice.setIgstTotal(new BigDecimal("180.00"));
        invoice.setGrandTotal(new BigDecimal("1180.00"));
        invoice.setAccountingStatus(AccountingStatus.NOT_POSTED);
        assertDoesNotThrow(() -> posting.postSalesInvoice(invoice));
        JournalEntry existing = new JournalEntry();
        existing.setId(UUID.randomUUID());
        when(journals.findByOrganizationIdAndSourceAndReferenceId(orgId, JournalSource.SALES_INVOICE, invoice.getId()))
                .thenReturn(Optional.of(existing));
        invoice.setAccountingStatus(AccountingStatus.NOT_POSTED);
        invoice.setPostedJournalEntryId(null);
        assertDoesNotThrow(() -> posting.postSalesInvoice(invoice));
    }

    @Test
    void postsSalesInvoiceWithCgstSgst() {
        SalesInvoice invoice = new SalesInvoice();
        invoice.setId(UUID.randomUUID());
        invoice.setOrganizationId(orgId);
        invoice.setCustomerId(UUID.randomUUID());
        invoice.setInvoiceNumber("INV-2");
        invoice.setInvoiceDate(LocalDate.of(2026, 7, 1));
        invoice.setTaxableAmount(new BigDecimal("1000.00"));
        invoice.setCgstTotal(new BigDecimal("90.00"));
        invoice.setSgstTotal(new BigDecimal("90.00"));
        invoice.setGrandTotal(new BigDecimal("1180.00"));
        invoice.setAccountingStatus(AccountingStatus.NOT_POSTED);
        assertDoesNotThrow(() -> posting.postSalesInvoice(invoice));
    }
}
