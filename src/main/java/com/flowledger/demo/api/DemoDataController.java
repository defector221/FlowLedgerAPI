package com.flowledger.demo.api;

import com.flowledger.common.dto.ApiResponse;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Legacy admin demo endpoints — migrated to {@code /api/v1/ops/demo/**} (platform JWT).
 * Kept as explicit redirects so old clients fail with a clear message.
 */
@RestController
@RequestMapping("/api/v1/admin/demo-data")
public class DemoDataController {

    @GetMapping("/scenarios")
    public ApiResponse<Map<String, String>> scenarios() {
        throw gone();
    }

    @PostMapping("/seed")
    public ApiResponse<Map<String, String>> seed() {
        throw gone();
    }

    private static ResponseStatusException gone() {
        return new ResponseStatusException(
                HttpStatus.GONE, "Demo APIs moved to /api/v1/ops/demo/** (platform operator JWT required)");
    }
}
