package com.flowledger.tax.controller;

import com.flowledger.tax.dto.TaxAdminDtos.SimulateRequest;
import com.flowledger.tax.dto.TaxCalculationDtos.TaxResult;
import com.flowledger.tax.service.TaxAdminService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tax/simulate")
public class TaxSimulationController {
    private final TaxAdminService service;

    public TaxSimulationController(TaxAdminService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('TAX_READ')")
    public TaxResult simulate(@Valid @RequestBody SimulateRequest request) {
        return service.simulate(request);
    }
}
