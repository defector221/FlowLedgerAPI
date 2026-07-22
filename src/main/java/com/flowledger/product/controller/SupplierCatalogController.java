package com.flowledger.product.controller;

import com.flowledger.product.dto.SupplierCatalogDtos.Create;
import com.flowledger.product.dto.SupplierCatalogDtos.Response;
import com.flowledger.product.dto.SupplierCatalogDtos.Update;
import com.flowledger.product.service.SupplierCatalogService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class SupplierCatalogController {
    private final SupplierCatalogService service;

    public SupplierCatalogController(SupplierCatalogService service) {
        this.service = service;
    }

    @GetMapping("/suppliers/{supplierId}/catalog")
    public List<Response> listBySupplier(@PathVariable UUID supplierId) {
        return service.listBySupplier(supplierId);
    }

    @GetMapping("/suppliers/{supplierId}/catalog/active")
    public List<Response> listActiveBySupplier(@PathVariable UUID supplierId) {
        return service.listActiveBySupplier(supplierId);
    }

    @GetMapping("/suppliers/{supplierId}/catalog/{id}")
    public Response get(@PathVariable UUID supplierId, @PathVariable UUID id) {
        return service.get(supplierId, id);
    }

    @PostMapping("/suppliers/{supplierId}/catalog")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ORGANIZATION_ADMIN', 'PURCHASE_MANAGER')")
    public Response create(@PathVariable UUID supplierId, @Valid @RequestBody Create dto) {
        return service.create(supplierId, dto);
    }

    @PutMapping("/suppliers/{supplierId}/catalog/{id}")
    @PreAuthorize("hasAnyRole('ORGANIZATION_ADMIN', 'PURCHASE_MANAGER')")
    public Response update(@PathVariable UUID supplierId, @PathVariable UUID id, @Valid @RequestBody Update dto) {
        return service.update(supplierId, id, dto);
    }

    @DeleteMapping("/suppliers/{supplierId}/catalog/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ORGANIZATION_ADMIN', 'PURCHASE_MANAGER')")
    public void delete(@PathVariable UUID supplierId, @PathVariable UUID id) {
        service.softDelete(supplierId, id);
    }

    @GetMapping("/products/{productId}/suppliers")
    public List<Response> listByProduct(@PathVariable UUID productId) {
        return service.listByProduct(productId);
    }

    @PostMapping("/products/{productId}/suppliers")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ORGANIZATION_ADMIN', 'PURCHASE_MANAGER')")
    public Response createForProduct(@PathVariable UUID productId, @Valid @RequestBody Create dto) {
        return service.createForProduct(productId, dto);
    }
}
