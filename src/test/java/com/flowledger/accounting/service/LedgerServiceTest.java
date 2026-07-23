package com.flowledger.accounting.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flowledger.accounting.dto.AccountingDtos.LedgerLineResponse;
import com.flowledger.accounting.dto.LedgerLineView;
import com.flowledger.accounting.entity.Account;
import com.flowledger.accounting.repository.AccountRepository;
import com.flowledger.accounting.repository.JournalEntryLineRepository;
import com.flowledger.common.exception.ResourceNotFoundException;
import com.flowledger.common.tenant.TenantContext;
import com.flowledger.customer.entity.Customer;
import com.flowledger.customer.repository.CustomerRepository;
import com.flowledger.supplier.repository.SupplierRepository;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LedgerServiceTest {
    UUID orgId = UUID.randomUUID();
    UUID customerId = UUID.randomUUID();
    AtomicReference<List<LedgerLineView>> customerRows = new AtomicReference<>(List.of());
    AtomicReference<Optional<Customer>> customer = new AtomicReference<>(Optional.empty());
    AtomicReference<Optional<Account>> account = new AtomicReference<>(Optional.empty());
    LedgerService service;

    @BeforeEach
    void setUp() {
        JournalEntryLineRepository lines = proxy(JournalEntryLineRepository.class, (method, args) -> {
            return switch (method.getName()) {
                case "findPostedLedgerForCustomer" -> customerRows.get();
                case "findPostedLedgerForAccount", "findPostedLedgerForSupplier" -> List.of();
                default -> defaultValue(method.getReturnType());
            };
        });
        AccountRepository accounts = proxy(AccountRepository.class, (method, args) -> {
            if ("findByIdAndOrganizationId".equals(method.getName())) {
                return account.get();
            }
            return defaultValue(method.getReturnType());
        });
        CustomerRepository customers = proxy(CustomerRepository.class, (method, args) -> {
            if ("findByIdAndOrganizationId".equals(method.getName())) {
                return customer.get();
            }
            return defaultValue(method.getReturnType());
        });
        SupplierRepository suppliers = proxy(SupplierRepository.class, (method, args) -> Optional.empty());
        service = new LedgerService(lines, accounts, customers, suppliers);
        TenantContext.set(orgId, UUID.randomUUID());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void customerLedgerRejectsInvalidDateRange() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.customerLedger(customerId, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1)));
    }

    @Test
    void customerLedgerRequiresOrgCustomer() {
        assertThrows(ResourceNotFoundException.class, () -> service.customerLedger(customerId, null, null));
    }

    @Test
    void customerLedgerBuildsRunningBalanceFromJoinedRows() {
        Customer customerEntity = new Customer();
        customerEntity.setId(customerId);
        customer.set(Optional.of(customerEntity));

        UUID journalId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        customerRows.set(List.of(
                new LedgerLineView(
                        journalId,
                        "JV/1",
                        LocalDate.of(2026, 4, 1),
                        "AR",
                        new BigDecimal("100.0000"),
                        BigDecimal.ZERO,
                        accountId,
                        "1100",
                        "Accounts Receivable",
                        customerId,
                        null,
                        1),
                new LedgerLineView(
                        journalId,
                        "JV/1",
                        LocalDate.of(2026, 4, 1),
                        "Receipt",
                        BigDecimal.ZERO,
                        new BigDecimal("40.0000"),
                        accountId,
                        "1100",
                        "Accounts Receivable",
                        customerId,
                        null,
                        2)));

        List<LedgerLineResponse> result = service.customerLedger(customerId, null, null);
        assertEquals(2, result.size());
        assertEquals(new BigDecimal("100.0000"), result.get(0).runningBalance());
        assertEquals(new BigDecimal("60.0000"), result.get(1).runningBalance());
        assertEquals("1100", result.get(0).accountCode());
    }

    @Test
    void accountLedgerRequiresOrgAccount() {
        UUID accountId = UUID.randomUUID();
        assertThrows(ResourceNotFoundException.class, () -> service.accountLedger(accountId, null, null));
    }

    @Test
    void accountLedgerReturnsEmptyWhenNoPostedLines() {
        UUID accountId = UUID.randomUUID();
        Account accountEntity = new Account();
        accountEntity.setId(accountId);
        account.set(Optional.of(accountEntity));
        assertTrue(service.accountLedger(accountId, null, null).isEmpty());
    }

    @FunctionalInterface
    interface Handler {
        Object invoke(java.lang.reflect.Method method, Object[] args) throws Throwable;
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Handler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (p, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> type.getSimpleName() + "Proxy";
                    case "hashCode" -> System.identityHashCode(p);
                    case "equals" -> p == args[0];
                    default -> null;
                };
            }
            return handler.invoke(method, args);
        });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            if (type == Optional.class) return Optional.empty();
            if (type == List.class) return List.of();
            if (type == boolean.class || type == Boolean.class) return false;
            return null;
        }
        if (type == boolean.class) return false;
        if (type == long.class) return 0L;
        if (type == int.class) return 0;
        return null;
    }
}
