package com.flowledger.report.controller;

import com.flowledger.report.dto.ReportFilter;
import com.flowledger.report.service.ReportService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {
    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/{name}")
    public List<Map<String, Object>> get(
            @PathVariable String name,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) UUID customer,
            @RequestParam(required = false) UUID supplier,
            @RequestParam(required = false) UUID product,
            @RequestParam(required = false) UUID category,
            @RequestParam(required = false) UUID warehouse) {
        return reports.report(name, new ReportFilter(from, to, customer, supplier, product, category, warehouse));
    }

    @GetMapping(value = "/{name}/export", produces = "text/csv")
    public ResponseEntity<String> csv(
            @PathVariable String name,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        List<Map<String, Object>> rows = reports.report(name, new ReportFilter(from, to, null, null, null, null, null));
        StringBuilder csv = new StringBuilder();
        if (!rows.isEmpty()) {
            csv.append(String.join(",", rows.get(0).keySet())).append('\n');
            for (Map<String, Object> r : rows) {
                csv.append(r.values().stream()
                                .map(v -> "\"" + String.valueOf(v).replace("\"", "\"\"") + "\"")
                                .collect(Collectors.joining(",")))
                        .append('\n');
            }
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + name + ".csv")
                .body(csv.toString());
    }
}
