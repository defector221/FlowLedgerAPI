package com.flowledger.retail.controller;

import static com.flowledger.retail.dto.RetailDtos.*;

import com.flowledger.retail.service.RetailAnalyticsService;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/retail/analytics")
public class RetailAnalyticsController {
    private final RetailAnalyticsService service;

    public RetailAnalyticsController(RetailAnalyticsService service) {
        this.service = service;
    }

    @GetMapping("/daily-sales")
    @PreAuthorize("hasAuthority('RETAIL_VIEW')")
    public DailySalesResponse dailySales(
            @RequestParam UUID storeId, @RequestParam(required = false) LocalDate date) {
        return service.dailySales(storeId, date);
    }
}
