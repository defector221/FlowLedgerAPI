package com.flowledger.barcode.controller;

import static com.flowledger.barcode.dto.BarcodeDtos.*;

import com.flowledger.barcode.service.BarcodeResolveService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/scan")
public class ScanResolveController {
    private final BarcodeResolveService resolveService;

    public ScanResolveController(BarcodeResolveService resolveService) {
        this.resolveService = resolveService;
    }

    @PostMapping("/resolve")
    @PreAuthorize("hasAnyAuthority('RETAIL_POS', 'INVENTORY_READ', 'PRODUCT_READ', 'PURCHASE_WRITE')")
    public ScanResolveResponse resolve(@Valid @RequestBody ScanResolveRequest request) {
        return resolveService.resolve(request);
    }
}
