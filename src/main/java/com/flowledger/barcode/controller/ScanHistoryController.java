package com.flowledger.barcode.controller;

import static com.flowledger.barcode.dto.ScanHistoryDtos.Response;

import com.flowledger.barcode.service.ScanHistoryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/scan")
public class ScanHistoryController {
    private final ScanHistoryService service;

    public ScanHistoryController(ScanHistoryService service) {
        this.service = service;
    }

    @GetMapping("/history")
    @PreAuthorize("hasAuthority('BARCODE_READ') or hasAuthority('PRODUCT_READ')")
    public Page<Response> history(Pageable pageable) {
        return service.list(pageable);
    }
}
