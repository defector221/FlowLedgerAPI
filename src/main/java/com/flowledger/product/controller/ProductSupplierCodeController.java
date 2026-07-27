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
@RequestMapping("/api/v1/products/{productId}/supplier-codes")
public class ProductSupplierCodeController {
    private final SupplierCatalogService service;

    public ProductSupplierCodeController(SupplierCatalogService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('BARCODE_READ') or hasAuthority('PRODUCT_READ') or hasAuthority('SUPPLIER_READ')")
    public List<Response> list(@PathVariable UUID productId) {
        return service.listByProduct(productId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('BARCODE_WRITE') or hasAuthority('PRODUCT_WRITE') or hasAuthority('PURCHASE_WRITE')")
    public Response create(@PathVariable UUID productId, @Valid @RequestBody Create dto) {
        return service.createForProduct(productId, dto);
    }

    @PutMapping("/{catalogItemId}")
    @PreAuthorize("hasAuthority('BARCODE_WRITE') or hasAuthority('PRODUCT_WRITE') or hasAuthority('PURCHASE_WRITE')")
    public Response update(
            @PathVariable UUID productId,
            @PathVariable UUID catalogItemId,
            @Valid @RequestBody Update dto) {
        return service.updateForProduct(productId, catalogItemId, dto);
    }

    @DeleteMapping("/{catalogItemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('BARCODE_WRITE') or hasAuthority('PRODUCT_WRITE') or hasAuthority('PURCHASE_WRITE')")
    public void delete(@PathVariable UUID productId, @PathVariable UUID catalogItemId) {
        service.softDeleteForProduct(productId, catalogItemId);
    }
}
