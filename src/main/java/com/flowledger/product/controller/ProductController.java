package com.flowledger.product.controller;

import com.flowledger.product.dto.ProductDtos.*;
import com.flowledger.product.service.ProductService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {
    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @GetMapping
    public Page<Response> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            Pageable pageable) {
        return service.search(new Search(search, active), pageable);
    }

    @GetMapping("/{id}")
    public Response get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ORGANIZATION_ADMIN', 'INVENTORY_MANAGER', 'ACCOUNTANT')")
    public Response create(@Valid @RequestBody Create dto) {
        return service.create(dto);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ORGANIZATION_ADMIN', 'INVENTORY_MANAGER', 'ACCOUNTANT')")
    public Response update(@PathVariable UUID id, @Valid @RequestBody Update dto) {
        return service.update(id, dto);
    }
}
