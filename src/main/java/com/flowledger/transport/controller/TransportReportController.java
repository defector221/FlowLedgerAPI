package com.flowledger.transport.controller;

import com.flowledger.transport.service.TransportReportService;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/transport/reports")
@PreAuthorize("hasAuthority('TRANSPORT_VIEW')")
public class TransportReportController {
    private final TransportReportService service;

    public TransportReportController(TransportReportService service) {
        this.service = service;
    }

    @GetMapping("/{name}")
    public ResponseEntity<?> report(@PathVariable String name, @RequestParam(defaultValue = "json") String format) {
        List<Map<String, Object>> rows = service.report(name);
        if (!"csv".equalsIgnoreCase(format)) return ResponseEntity.ok(rows);
        byte[] csv = csv(rows).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }

    private String csv(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) return "";
        List<String> headers = new ArrayList<>(rows.get(0).keySet());
        StringBuilder out = new StringBuilder(String.join(",", headers)).append('\n');
        for (Map<String, Object> row : rows) {
            out.append(headers.stream()
                            .map(h -> quote(row.get(h)))
                            .reduce((a, b) -> a + "," + b)
                            .orElse(""))
                    .append('\n');
        }
        return out.toString();
    }

    private String quote(Object value) {
        return "\"" + String.valueOf(value == null ? "" : value).replace("\"", "\"\"") + "\"";
    }
}
