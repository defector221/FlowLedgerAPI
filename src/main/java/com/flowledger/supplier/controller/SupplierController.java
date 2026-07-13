package com.flowledger.supplier.controller;

import com.flowledger.supplier.dto.SupplierDtos.*;
import com.flowledger.supplier.service.SupplierService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/suppliers")
public class SupplierController {
    private final SupplierService service;

    public SupplierController(SupplierService service) {
        this.service = service;
    }

    @GetMapping
    public Page<Response> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean archived,
            Pageable pageable) {
        return service.search(new Search(search, archived), pageable);
    }

    @GetMapping("/{id}")
    public Response get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ORGANIZATION_ADMIN', 'PURCHASE_MANAGER')")
    public Response create(@Valid @RequestBody Create dto) {
        return service.create(dto);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ORGANIZATION_ADMIN', 'PURCHASE_MANAGER')")
    public Response update(@PathVariable UUID id, @Valid @RequestBody Update dto) {
        return service.update(id, dto);
    }
}
