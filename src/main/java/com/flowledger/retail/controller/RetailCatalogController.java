package com.flowledger.retail.controller;

import static com.flowledger.retail.dto.RetailDtos.*;

import com.flowledger.retail.service.RetailCatalogService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/retail/catalog")
public class RetailCatalogController {
    private final RetailCatalogService service;

    public RetailCatalogController(RetailCatalogService service) {
        this.service = service;
    }

    // ------------------------------------------------------------------ Brands
    @GetMapping("/brands")
    @PreAuthorize("hasAuthority('RETAIL_VIEW')")
    public List<BrandResponse> listBrands() {
        return service.listBrands();
    }

    @PostMapping("/brands")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('RETAIL_STORE_MANAGE')")
    public BrandResponse createBrand(@Valid @RequestBody BrandRequest r) {
        return service.createBrand(r);
    }

    @PutMapping("/brands/{id}")
    @PreAuthorize("hasAuthority('RETAIL_STORE_MANAGE')")
    public BrandResponse updateBrand(@PathVariable UUID id, @Valid @RequestBody BrandRequest r) {
        return service.updateBrand(id, r);
    }

    @DeleteMapping("/brands/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('RETAIL_ADMIN')")
    public void deleteBrand(@PathVariable UUID id) {
        service.deleteBrand(id);
    }

    // ------------------------------------------------------------- Departments
    @GetMapping("/departments")
    @PreAuthorize("hasAuthority('RETAIL_VIEW')")
    public List<DepartmentResponse> listDepartments() {
        return service.listDepartments();
    }

    @PostMapping("/departments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('RETAIL_STORE_MANAGE')")
    public DepartmentResponse createDepartment(@Valid @RequestBody DepartmentRequest r) {
        return service.createDepartment(r);
    }

    @PutMapping("/departments/{id}")
    @PreAuthorize("hasAuthority('RETAIL_STORE_MANAGE')")
    public DepartmentResponse updateDepartment(
            @PathVariable UUID id, @Valid @RequestBody DepartmentRequest r) {
        return service.updateDepartment(id, r);
    }

    @DeleteMapping("/departments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('RETAIL_ADMIN')")
    public void deleteDepartment(@PathVariable UUID id) {
        service.deleteDepartment(id);
    }

    // ------------------------------------------------------------- Collections
    @GetMapping("/collections")
    @PreAuthorize("hasAuthority('RETAIL_VIEW')")
    public List<CollectionResponse> listCollections() {
        return service.listCollections();
    }

    @PostMapping("/collections")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('RETAIL_STORE_MANAGE')")
    public CollectionResponse createCollection(@Valid @RequestBody CollectionRequest r) {
        return service.createCollection(r);
    }

    @PutMapping("/collections/{id}")
    @PreAuthorize("hasAuthority('RETAIL_STORE_MANAGE')")
    public CollectionResponse updateCollection(
            @PathVariable UUID id, @Valid @RequestBody CollectionRequest r) {
        return service.updateCollection(id, r);
    }

    @DeleteMapping("/collections/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('RETAIL_ADMIN')")
    public void deleteCollection(@PathVariable UUID id) {
        service.deleteCollection(id);
    }

    // ---------------------------------------------------------------- Variants
    @GetMapping("/variants")
    @PreAuthorize("hasAuthority('RETAIL_VIEW')")
    public List<VariantResponse> listVariants(@RequestParam UUID parentProductId) {
        return service.listVariants(parentProductId);
    }

    @PostMapping("/variants")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('RETAIL_STORE_MANAGE')")
    public VariantResponse createVariant(@Valid @RequestBody VariantRequest r) {
        return service.createVariant(r);
    }

    @PutMapping("/variants/{id}")
    @PreAuthorize("hasAuthority('RETAIL_STORE_MANAGE')")
    public VariantResponse updateVariant(@PathVariable UUID id, @Valid @RequestBody VariantRequest r) {
        return service.updateVariant(id, r);
    }

    @DeleteMapping("/variants/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('RETAIL_ADMIN')")
    public void deleteVariant(@PathVariable UUID id) {
        service.deleteVariant(id);
    }

    // ---------------------------------------------------------------- Barcodes
    @GetMapping("/barcodes")
    @PreAuthorize("hasAuthority('RETAIL_VIEW')")
    public List<BarcodeResponse> listBarcodes(@RequestParam UUID productId) {
        return service.listBarcodes(productId);
    }

    @PostMapping("/barcodes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('RETAIL_STORE_MANAGE')")
    public BarcodeResponse createBarcode(@Valid @RequestBody BarcodeRequest r) {
        return service.createBarcode(r);
    }

    @DeleteMapping("/barcodes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('RETAIL_ADMIN')")
    public void deleteBarcode(@PathVariable UUID id) {
        service.deleteBarcode(id);
    }
}
