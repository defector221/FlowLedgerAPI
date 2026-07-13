package com.flowledger.product.controller;

import com.flowledger.product.dto.CategoryDtos.*;
import com.flowledger.product.service.CategoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {
    private final CategoryService service;

    public CategoryController(CategoryService service) {
        this.service = service;
    }

    @GetMapping
    public List<Response> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public Response get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ORGANIZATION_ADMIN', 'INVENTORY_MANAGER')")
    public Response create(@Valid @RequestBody Create dto) {
        return service.create(dto);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ORGANIZATION_ADMIN', 'INVENTORY_MANAGER')")
    public Response update(@PathVariable UUID id, @Valid @RequestBody Update dto) {
        return service.update(id, dto);
    }
}
