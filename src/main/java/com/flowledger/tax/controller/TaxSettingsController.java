package com.flowledger.tax.controller;

import com.flowledger.tax.dto.TaxAdminDtos.*;
import com.flowledger.tax.service.TaxAdminService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tax/settings")
public class TaxSettingsController {
    private final TaxAdminService service;

    public TaxSettingsController(TaxAdminService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('TAX_READ')")
    public SettingsResponse get() {
        return service.getSettings();
    }

    @PutMapping
    @PreAuthorize("hasAuthority('TAX_ADMIN')")
    public SettingsResponse update(@Valid @RequestBody SettingsRequest request) {
        return service.upsertSettings(request);
    }
}
