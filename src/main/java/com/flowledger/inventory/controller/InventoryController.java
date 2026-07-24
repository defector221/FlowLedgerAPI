package com.flowledger.inventory.controller;

import com.flowledger.common.dto.PageResponse;
import com.flowledger.inventory.dto.InventoryDtos.*;
import com.flowledger.inventory.entity.InventoryTransaction;
import com.flowledger.inventory.service.InventoryService;
import com.flowledger.report.dto.ReportFilter;
import com.flowledger.report.service.ReportService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {
    private final InventoryService service;
    private final ReportService reports;

    public InventoryController(InventoryService service, ReportService reports) {
        this.service = service;
        this.reports = reports;
    }

    @PostMapping("/transactions")
    @ResponseStatus(HttpStatus.CREATED)
    public InventoryTransaction post(@Valid @RequestBody PostTransaction d) {
        return service.postTransaction(d);
    }

    @GetMapping("/stock/{productId}")
    public Stock stock(@PathVariable UUID productId, @RequestParam(required = false) UUID warehouseId) {
        return service.getStock(productId, warehouseId);
    }

    @GetMapping("/overview")
    public PageResponse<StockPosition> overview(
            @RequestParam(required = false) String q, @PageableDefault(size = 20) Pageable pageable) {
        return service.stockOverview(q, pageable);
    }

    @GetMapping("/ledger/{productId}")
    public PageResponse<Ledger> ledger(
            @PathVariable UUID productId,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @PageableDefault(size = 50) Pageable pageable) {
        return service.getStockLedger(productId, warehouseId, from, to, pageable);
    }

    @PostMapping("/adjustments")
    @PreAuthorize("hasAnyRole('ORGANIZATION_ADMIN','INVENTORY_MANAGER')")
    public InventoryTransaction adjust(@Valid @RequestBody Adjustment d) {
        return service.adjustStock(d);
    }

    @PostMapping("/transfers")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ORGANIZATION_ADMIN','INVENTORY_MANAGER')")
    public void transfer(@Valid @RequestBody Transfer d) {
        service.transferStock(d);
    }

    @PostMapping("/opening-stock")
    public InventoryTransaction opening(@Valid @RequestBody Adjustment d) {
        return service.openingStock(d);
    }

    @GetMapping("/alerts/low-stock")
    public List<Alert> low() {
        return service.lowStockAlerts(false);
    }

    @GetMapping("/alerts/reorder")
    public List<Alert> reorder() {
        return service.lowStockAlerts(true);
    }

    @GetMapping("/reports/stock-aging")
    public List<Map<String, Object>> stockAging(@RequestParam(required = false) LocalDate asOf) {
        LocalDate to = asOf != null ? asOf : LocalDate.now();
        return reports.report("stock-aging", new ReportFilter(null, to, null, null, null, null, null));
    }
}
