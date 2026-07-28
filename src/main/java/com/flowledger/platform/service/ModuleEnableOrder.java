package com.flowledger.platform.service;

import com.flowledger.platform.dto.PlatformDtos.UpsertOrganizationModuleRequest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** Topological order for batch module enables so dependents (RETAIL) run after dependencies. */
final class ModuleEnableOrder {
    private ModuleEnableOrder() {}

    static List<UpsertOrganizationModuleRequest> order(
            List<UpsertOrganizationModuleRequest> requests, Function<String, List<String>> dependenciesOf) {
        if (requests == null || requests.size() <= 1) {
            return requests == null ? List.of() : requests;
        }
        Map<String, UpsertOrganizationModuleRequest> byCode = new LinkedHashMap<>();
        for (UpsertOrganizationModuleRequest r : requests) {
            byCode.put(r.moduleCode(), r);
        }
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        List<UpsertOrganizationModuleRequest> ordered = new ArrayList<>();
        for (String code : byCode.keySet()) {
            visit(code, byCode, visiting, visited, ordered, dependenciesOf);
        }
        return ordered;
    }

    private static void visit(
            String code,
            Map<String, UpsertOrganizationModuleRequest> byCode,
            Set<String> visiting,
            Set<String> visited,
            List<UpsertOrganizationModuleRequest> ordered,
            Function<String, List<String>> dependenciesOf) {
        if (visited.contains(code) || !byCode.containsKey(code)) {
            return;
        }
        if (!visiting.add(code)) {
            return;
        }
        UpsertOrganizationModuleRequest req = byCode.get(code);
        if (Boolean.TRUE.equals(req.enabled())) {
            for (String dep : dependenciesOf.apply(code)) {
                visit(dep, byCode, visiting, visited, ordered, dependenciesOf);
            }
        }
        visiting.remove(code);
        visited.add(code);
        ordered.add(req);
    }
}
