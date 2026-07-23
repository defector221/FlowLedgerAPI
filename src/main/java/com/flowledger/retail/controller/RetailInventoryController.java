package com.flowledger.retail.controller;

import static com.flowledger.retail.dto.RetailDtos.*;

import com.flowledger.retail.service.RetailInventoryService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/retail/inventory")
public class RetailInventoryController {
    private final RetailInventoryService service;

    public RetailInventoryController(RetailInventoryService service) {
        this.service = service;
    }

    // --------------------------------------------------------------- Locations
    @GetMapping("/locations")
    @PreAuthorize("hasAuthority('RETAIL_VIEW')")
    public List<LocationResponse> listLocations(@RequestParam UUID storeId) {
        return service.listLocations(storeId);
    }

    @PostMapping("/locations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('RETAIL_STORE_MANAGE')")
    public LocationResponse createLocation(@Valid @RequestBody LocationRequest r) {
        return service.createLocation(r);
    }

    @PutMapping("/locations/{id}")
    @PreAuthorize("hasAuthority('RETAIL_STORE_MANAGE')")
    public LocationResponse updateLocation(@PathVariable UUID id, @Valid @RequestBody LocationRequest r) {
        return service.updateLocation(id, r);
    }

    @DeleteMapping("/locations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('RETAIL_ADMIN')")
    public void deleteLocation(@PathVariable UUID id) {
        service.deleteLocation(id);
    }

    // ------------------------------------------------------------- Stock counts
    @GetMapping("/stock-counts")
    @PreAuthorize("hasAuthority('RETAIL_VIEW')")
    public List<StockCountResponse> listStockCounts() {
        return service.listStockCounts();
    }

    @GetMapping("/stock-counts/{id}")
    @PreAuthorize("hasAuthority('RETAIL_VIEW')")
    public StockCountResponse getStockCount(@PathVariable UUID id) {
        return service.getStockCount(id);
    }

    @PostMapping("/stock-counts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('RETAIL_STORE_MANAGE')")
    public StockCountResponse createStockCount(@Valid @RequestBody StockCountRequest r) {
        return service.createStockCount(r);
    }

    @PostMapping("/stock-counts/{id}/lines")
    @PreAuthorize("hasAuthority('RETAIL_STORE_MANAGE')")
    public StockCountResponse addLines(@PathVariable UUID id, @Valid @RequestBody List<StockCountLineRequest> lines) {
        return service.addLines(id, lines);
    }

    @PostMapping("/stock-counts/{id}/complete")
    @PreAuthorize("hasAuthority('RETAIL_STORE_MANAGE')")
    public StockCountResponse complete(@PathVariable UUID id) {
        return service.complete(id);
    }
}
