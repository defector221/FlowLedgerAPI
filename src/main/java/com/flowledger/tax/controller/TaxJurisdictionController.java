package com.flowledger.tax.controller;

import com.flowledger.tax.dto.TaxAdminDtos.*;
import com.flowledger.tax.service.TaxAdminService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tax/jurisdictions")
public class TaxJurisdictionController {
    private final TaxAdminService service;

    public TaxJurisdictionController(TaxAdminService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('TAX_READ')")
    public List<JurisdictionResponse> list() {
        return service.listJurisdictions();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('TAX_WRITE')")
    public JurisdictionResponse create(@Valid @RequestBody JurisdictionRequest request) {
        return service.createJurisdiction(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('TAX_WRITE')")
    public JurisdictionResponse update(@PathVariable UUID id, @Valid @RequestBody JurisdictionRequest request) {
        return service.updateJurisdiction(id, request);
    }
}
