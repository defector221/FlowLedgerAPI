package com.flowledger.retail.controller;

import static com.flowledger.retail.dto.RetailDtos.*;

import com.flowledger.retail.service.RetailSyncService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/retail/pos/sync")
public class RetailSyncController {
    private final RetailSyncService service;

    public RetailSyncController(RetailSyncService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('RETAIL_POS')")
    public SyncResponse accept(@Valid @RequestBody SyncRequest request) {
        return service.accept(request);
    }
}
