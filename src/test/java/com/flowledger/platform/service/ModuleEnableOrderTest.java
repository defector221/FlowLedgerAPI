package com.flowledger.platform.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flowledger.platform.dto.PlatformDtos.UpsertOrganizationModuleRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ModuleEnableOrderTest {

    @Test
    void retailDependenciesEnabledBeforeRetail() {
        Map<String, List<String>> deps = Map.of(
                "RETAIL", List.of("INVENTORY", "ACCOUNTING"),
                "TRANSPORT", List.of("INVENTORY"),
                "WAREHOUSE", List.of("INVENTORY"));

        List<UpsertOrganizationModuleRequest> ordered = ModuleEnableOrder.order(
                List.of(req("RETAIL", true), req("ACCOUNTING", true), req("INVENTORY", true), req("TRANSPORT", true)),
                code -> deps.getOrDefault(code, List.of()));

        List<String> codes = ordered.stream()
                .map(UpsertOrganizationModuleRequest::moduleCode)
                .toList();
        assertTrue(codes.indexOf("INVENTORY") < codes.indexOf("RETAIL"));
        assertTrue(codes.indexOf("ACCOUNTING") < codes.indexOf("RETAIL"));
        assertTrue(codes.indexOf("INVENTORY") < codes.indexOf("TRANSPORT"));
        assertEquals(4, codes.size());
    }

    private static UpsertOrganizationModuleRequest req(String code, boolean enabled) {
        return new UpsertOrganizationModuleRequest(code, enabled, true, false, null, null);
    }
}
