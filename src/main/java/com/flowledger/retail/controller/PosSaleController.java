package com.flowledger.retail.controller;

import static com.flowledger.retail.dto.RetailDtos.*;

import com.flowledger.retail.domain.RetailEnums.PosSaleStatus;
import com.flowledger.retail.service.PosSaleService;
import com.flowledger.retail.service.RetailCatalogService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/retail/pos")
public class PosSaleController {
    private final PosSaleService sales;
    private final RetailCatalogService catalog;

    public PosSaleController(PosSaleService sales, RetailCatalogService catalog) {
        this.sales = sales;
        this.catalog = catalog;
    }

    @GetMapping("/sales")
    @PreAuthorize("hasAuthority('RETAIL_VIEW')")
    public List<PosSaleResponse> list(@RequestParam(required = false) PosSaleStatus status) {
        return sales.list(status);
    }

    @GetMapping("/sales/{id}")
    @PreAuthorize("hasAuthority('RETAIL_VIEW')")
    public PosSaleResponse get(@PathVariable UUID id) {
        return sales.get(id);
    }

    @PostMapping("/sales")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('RETAIL_POS')")
    public PosSaleResponse createDraft(@Valid @RequestBody PosSaleRequest r) {
        return sales.createDraft(r);
    }

    @PostMapping("/sales/{id}/lines")
    @PreAuthorize("hasAuthority('RETAIL_POS')")
    public PosSaleResponse addLine(@PathVariable UUID id, @Valid @RequestBody PosLineRequest r) {
        return sales.addLine(id, r);
    }

    @DeleteMapping("/sales/{id}/lines/{lineId}")
    @PreAuthorize("hasAuthority('RETAIL_POS')")
    public PosSaleResponse removeLine(@PathVariable UUID id, @PathVariable UUID lineId) {
        return sales.removeLine(id, lineId);
    }

    @PostMapping("/sales/{id}/hold")
    @PreAuthorize("hasAuthority('RETAIL_POS')")
    public PosSaleResponse hold(@PathVariable UUID id, @RequestBody(required = false) HoldRequest r) {
        return sales.hold(id, r == null ? new HoldRequest(null) : r);
    }

    @PostMapping("/sales/{id}/resume")
    @PreAuthorize("hasAuthority('RETAIL_POS')")
    public PosSaleResponse resume(@PathVariable UUID id) {
        return sales.resume(id);
    }

    @PostMapping("/sales/{id}/void")
    @PreAuthorize("hasAuthority('RETAIL_POS')")
    public PosSaleResponse voidSale(@PathVariable UUID id) {
        return sales.voidSale(id);
    }

    @PostMapping("/sales/{id}/checkout")
    @PreAuthorize("hasAuthority('RETAIL_POS')")
    public PosSaleResponse checkout(@PathVariable UUID id, @Valid @RequestBody CheckoutRequest r) {
        return sales.checkout(id, r);
    }

    @GetMapping("/products")
    @PreAuthorize("hasAuthority('RETAIL_POS')")
    public ProductLookupResponse lookupProduct(
            @RequestParam(required = false) String barcode, @RequestParam(required = false) String q) {
        String code = barcode != null && !barcode.isBlank() ? barcode : q;
        if (code == null || code.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "barcode or q is required");
        }
        return catalog.lookupByBarcode(code.trim());
    }
}
