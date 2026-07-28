package com.flowledger.demo.api;

import com.flowledger.common.dto.ApiResponse;
import com.flowledger.demo.DemoDataOrchestrator;
import com.flowledger.demo.DemoDataOrchestrator.DemoDisabledException;
import com.flowledger.demo.DemoSeedRequest;
import com.flowledger.demo.DemoSeedResult;
import com.flowledger.demo.config.DemoProperties;
import com.flowledger.demo.scenario.DemoScenarioRegistry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/admin/demo-data")
public class DemoDataController {
    private final DemoProperties props;
    private final DemoScenarioRegistry registry;
    private final DemoDataOrchestrator orchestrator;

    public DemoDataController(
            DemoProperties props, DemoScenarioRegistry registry, DemoDataOrchestrator orchestrator) {
        this.props = props;
        this.registry = registry;
        this.orchestrator = orchestrator;
    }

    @GetMapping("/scenarios")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<List<Map<String, Object>>> scenarios() {
        requireEnabled();
        List<Map<String, Object>> list = registry.all().stream()
                .map(s -> {
                    var bp = registry.require(s);
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("command", s.command());
                    m.put("organizationName", s.organizationName());
                    m.put("description", s.description());
                    m.put("branchCount", bp.branchCount());
                    m.put("storeCount", bp.storeCount());
                    m.put("productCount", bp.productCount());
                    m.put("estimatedMinutes", bp.estimatedMinutes());
                    return m;
                })
                .toList();
        return ApiResponse.of(list);
    }

    @PostMapping("/seed")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<DemoSeedResult> seed(@RequestBody(required = false) DemoSeedRequest request) {
        requireEnabled();
        try {
            return ApiResponse.of(orchestrator.seed(request == null ? new DemoSeedRequest(null, null, null, null) : request));
        } catch (DemoDisabledException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    private void requireEnabled() {
        if (!props.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }
}
