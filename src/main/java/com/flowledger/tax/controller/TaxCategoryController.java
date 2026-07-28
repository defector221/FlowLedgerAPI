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
@RequestMapping("/api/v1/tax/categories")
public class TaxCategoryController {
    private final TaxAdminService service;

    public TaxCategoryController(TaxAdminService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('TAX_READ')")
    public List<CategoryResponse> list() {
        return service.listCategories();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('TAX_READ')")
    public CategoryResponse get(@PathVariable UUID id) {
        return service.getCategory(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('TAX_WRITE')")
    public CategoryResponse create(@Valid @RequestBody CategoryRequest request) {
        return service.createCategory(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('TAX_WRITE')")
    public CategoryResponse update(@PathVariable UUID id, @Valid @RequestBody CategoryRequest request) {
        return service.updateCategory(id, request);
    }
}
