package com.flowledger.barcode.controller;

import static com.flowledger.barcode.dto.ProductBarcodeDtos.*;

import com.flowledger.barcode.service.ProductBarcodeManagementService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/products")
public class ProductBarcodeController {
    private final ProductBarcodeManagementService service;

    public ProductBarcodeController(ProductBarcodeManagementService service) {
        this.service = service;
    }

    @GetMapping("/{productId}/barcodes")
    @PreAuthorize("hasAuthority('BARCODE_READ') or hasAuthority('PRODUCT_READ')")
    public List<BarcodeResponse> list(@PathVariable UUID productId) {
        return service.list(productId);
    }

    @PostMapping("/{productId}/barcodes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('BARCODE_WRITE') or hasAuthority('PRODUCT_WRITE')")
    public BarcodeResponse create(@PathVariable UUID productId, @Valid @RequestBody CreateBarcodeRequest request) {
        return service.create(productId, request);
    }

    @PostMapping("/{productId}/barcode/generate")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('BARCODE_WRITE') or hasAuthority('PRODUCT_WRITE')")
    public BarcodeResponse generate(
            @PathVariable UUID productId, @RequestBody(required = false) GenerateBarcodeRequest request) {
        return service.generate(productId, request);
    }

    @PostMapping("/{productId}/barcode/regenerate")
    @PreAuthorize("hasAuthority('BARCODE_WRITE') or hasAuthority('PRODUCT_WRITE')")
    public BarcodeResponse regenerate(
            @PathVariable UUID productId, @RequestBody(required = false) RegenerateBarcodeRequest request) {
        return service.regenerate(productId, request);
    }

    @GetMapping("/{productId}/barcode-history")
    @PreAuthorize("hasAuthority('BARCODE_READ') or hasAuthority('PRODUCT_READ')")
    public List<BarcodeHistoryResponse> history(@PathVariable UUID productId) {
        return service.history(productId);
    }

    @DeleteMapping("/{productId}/barcodes/{barcodeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('BARCODE_WRITE') or hasAuthority('PRODUCT_WRITE')")
    public void delete(@PathVariable UUID productId, @PathVariable UUID barcodeId) {
        service.softDelete(productId, barcodeId, null);
    }

    @PostMapping("/barcodes/bulk-generate")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority('BARCODE_WRITE') or hasAuthority('PRODUCT_WRITE')")
    public BulkGenerateJobResponse bulkGenerate(@RequestBody(required = false) BulkGenerateRequest request) {
        return service.bulkGenerate(request == null ? new BulkGenerateRequest(List.of()) : request);
    }
}
