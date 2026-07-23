package com.flowledger.retail.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.flowledger.common.tenant.TenantContext;
import com.flowledger.organization.entity.OrganizationSettings;
import com.flowledger.organization.repository.OrganizationSettingsRepository;
import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class RetailModuleGuardTest {
    UUID orgId = UUID.randomUUID();
    AtomicReference<Optional<OrganizationSettings>> settings = new AtomicReference<>(Optional.empty());
    RetailModuleGuard guard;

    @BeforeEach
    void setUp() {
        OrganizationSettingsRepository repo = proxy(OrganizationSettingsRepository.class, (method, args) -> {
            if ("findByOrganizationId".equals(method.getName())) {
                return settings.get();
            }
            return defaultValue(method.getReturnType());
        });
        guard = new RetailModuleGuard(repo);
        TenantContext.set(orgId, UUID.randomUUID());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void rejectsWhenRetailDisabled() {
        OrganizationSettings s = new OrganizationSettings();
        s.setRetailEnabled(false);
        settings.set(Optional.of(s));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> guard.ensureEnabled());
        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    void allowsWhenRetailEnabled() {
        OrganizationSettings s = new OrganizationSettings();
        s.setRetailEnabled(true);
        settings.set(Optional.of(s));
        assertEquals(orgId, guard.ensureEnabled());
    }

    @Test
    void rejectsWhenSettingsMissing() {
        settings.set(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> guard.ensureEnabled());
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
