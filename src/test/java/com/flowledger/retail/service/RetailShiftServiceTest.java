package com.flowledger.retail.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.flowledger.common.tenant.TenantContext;
import com.flowledger.organization.entity.OrganizationSettings;
import com.flowledger.organization.repository.OrganizationSettingsRepository;
import com.flowledger.retail.domain.RetailEnums.ShiftStatus;
import com.flowledger.retail.dto.RetailDtos.CloseShiftRequest;
import com.flowledger.retail.dto.RetailDtos.OpenShiftRequest;
import com.flowledger.retail.dto.RetailDtos.ShiftResponse;
import com.flowledger.retail.entity.RetailShift;
import com.flowledger.retail.repository.PosSalePaymentRepository;
import com.flowledger.retail.repository.PosSaleRepository;
import com.flowledger.retail.repository.RetailShiftRepository;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class RetailShiftServiceTest {
    UUID orgId = UUID.randomUUID();
    UUID storeId = UUID.randomUUID();
    UUID counterId = UUID.randomUUID();
    UUID cashierId = UUID.randomUUID();

    AtomicReference<Optional<OrganizationSettings>> settings = new AtomicReference<>();
    AtomicReference<List<RetailShift>> openForCashier = new AtomicReference<>(List.of());
    AtomicReference<RetailShift> saved = new AtomicReference<>();

    RetailShiftService service;

    @BeforeEach
    void setUp() {
        OrganizationSettings enabled = new OrganizationSettings();
        enabled.setRetailEnabled(true);
        settings.set(Optional.of(enabled));
        openForCashier.set(List.of());
        saved.set(null);

        RetailModuleGuard guard = new RetailModuleGuard(proxy(OrganizationSettingsRepository.class, (m, a) -> {
            if ("findByOrganizationId".equals(m.getName())) {
                return settings.get();
            }
            return defaultValue(m.getReturnType());
        }));

        RetailShiftRepository shifts = proxy(RetailShiftRepository.class, (m, a) -> switch (m.getName()) {
            case "findByOrganizationIdAndCashierIdAndStatusAndDeletedFalse" -> openForCashier.get();
            case "findByIdAndOrganizationIdAndDeletedFalse" -> Optional.ofNullable(saved.get());
            case "save" -> {
                RetailShift shift = (RetailShift) a[0];
                if (shift.getId() == null) {
                    shift.setId(UUID.randomUUID());
                }
                saved.set(shift);
                yield shift;
            }
            case "findByOrganizationIdAndDeletedFalseOrderByOpenedAtDesc",
                    "findByOrganizationIdAndStoreIdAndDeletedFalseOrderByOpenedAtDesc" ->
                    saved.get() == null ? List.of() : List.of(saved.get());
            default -> defaultValue(m.getReturnType());
        });

        PosSaleRepository posSales = proxy(PosSaleRepository.class, (m, a) -> List.of());
        PosSalePaymentRepository posPayments = proxy(PosSalePaymentRepository.class, (m, a) -> List.of());

        service = new RetailShiftService(guard, shifts, posSales, posPayments);
        TenantContext.set(orgId, UUID.randomUUID());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void openShiftRequiresRetailEnabled() {
        OrganizationSettings disabled = new OrganizationSettings();
        disabled.setRetailEnabled(false);
        settings.set(Optional.of(disabled));
        assertThrows(
                ResponseStatusException.class,
                () -> service.open(new OpenShiftRequest(storeId, counterId, null, cashierId, BigDecimal.TEN, null)));
    }

    @Test
    void openAndCloseShiftComputesVarianceFromFloat() {
        ShiftResponse opened =
                service.open(new OpenShiftRequest(storeId, counterId, null, cashierId, new BigDecimal("100.00"), null));
        assertEquals(ShiftStatus.OPEN, opened.status());

        ShiftResponse closed = service.close(opened.id(), new CloseShiftRequest(new BigDecimal("95.00"), "short"));
        assertEquals(ShiftStatus.CLOSED, closed.status());
        assertEquals(0, new BigDecimal("-5.00").compareTo(closed.variance()));
    }

    @Test
    void rejectsSecondOpenShiftForSameCashier() {
        service.open(new OpenShiftRequest(storeId, counterId, null, cashierId, BigDecimal.ZERO, null));
        openForCashier.set(new ArrayList<>(List.of(saved.get())));
        assertThrows(
                ResponseStatusException.class,
                () -> service.open(new OpenShiftRequest(storeId, counterId, null, cashierId, BigDecimal.ZERO, null)));
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Invoker invoker) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (p, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return method.invoke(p, args);
            }
            return invoker.invoke(method, args);
        });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == int.class) {
            return 0;
        }
        return null;
    }

    @FunctionalInterface
    private interface Invoker {
        Object invoke(java.lang.reflect.Method method, Object[] args) throws Throwable;
    }
}
