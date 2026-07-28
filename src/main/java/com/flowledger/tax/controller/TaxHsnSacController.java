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
@RequestMapping("/api/v1/tax/hsn-sac")
public class TaxHsnSacController {
    private final TaxAdminService service;

    public TaxHsnSacController(TaxAdminService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('TAX_READ')")
    public List<HsnSacResponse> list() {
        return service.listHsnSac();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('TAX_WRITE')")
    public HsnSacResponse create(@Valid @RequestBody HsnSacRequest request) {
        return service.createHsnSac(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('TAX_WRITE')")
    public HsnSacResponse update(@PathVariable UUID id, @Valid @RequestBody HsnSacRequest request) {
        return service.updateHsnSac(id, request);
    }
}
