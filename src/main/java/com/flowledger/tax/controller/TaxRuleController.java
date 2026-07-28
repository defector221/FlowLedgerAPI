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
@RequestMapping("/api/v1/tax/rules")
public class TaxRuleController {
    private final TaxAdminService service;

    public TaxRuleController(TaxAdminService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('TAX_READ')")
    public List<RuleResponse> list(@RequestParam UUID categoryId) {
        return service.listRules(categoryId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('TAX_READ')")
    public RuleResponse get(@PathVariable UUID id) {
        return service.getRule(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('TAX_ADMIN')")
    public RuleResponse createVersion(@Valid @RequestBody RuleRequest request) {
        return service.createRuleVersion(request);
    }
}
