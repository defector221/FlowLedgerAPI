package com.flowledger.tax.controller;

import com.flowledger.tax.entity.TaxRuleAuditLog;
import com.flowledger.tax.service.TaxAuditService;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tax/audit")
public class TaxAuditController {
    private final TaxAuditService service;

    public TaxAuditController(TaxAuditService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('TAX_READ')")
    public List<TaxRuleAuditLog> list(@RequestParam(required = false) UUID taxCategoryId) {
        return service.listAudit(taxCategoryId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('TAX_READ')")
    public TaxRuleAuditLog get(@PathVariable UUID id) {
        return service.getAudit(id);
    }
}
