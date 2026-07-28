package com.flowledger.location.controller;

import com.flowledger.common.dto.ApiResponse;
import com.flowledger.location.dto.LocationDtos.*;
import com.flowledger.location.service.CashDrawerService;
import com.flowledger.location.service.LocationContextService;
import com.flowledger.retail.dto.RetailDtos.StoreRequest;
import com.flowledger.retail.dto.RetailDtos.StoreResponse;
import com.flowledger.retail.dto.RetailDtos.TerminalResponse;
import com.flowledger.retail.service.RetailStoreService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class LocationController {
    private final LocationContextService context;
    private final RetailStoreService stores;
    private final CashDrawerService drawers;

    public LocationController(LocationContextService context, RetailStoreService stores, CashDrawerService drawers) {
        this.context = context;
        this.stores = stores;
        this.drawers = drawers;
    }

    @GetMapping("/location/context")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<AccessibleLocationResponse> accessible() {
        return ApiResponse.of(context.accessibleLocations());
    }

    @PostMapping("/auth/context")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<LocationContextResponse> switchContext(@Valid @RequestBody LocationContextRequest request) {
        return ApiResponse.of(context.switchContext(request));
    }

    @GetMapping("/stores")
    @PreAuthorize("hasAuthority('STORE_READ') or hasAuthority('RETAIL_VIEW') or hasAuthority('RETAIL_STORE_MANAGE')")
    public ApiResponse<List<StoreResponse>> listStores(@RequestParam(required = false) UUID branchId) {
        return ApiResponse.of(stores.listStores(branchId));
    }

    @GetMapping("/stores/{id}")
    @PreAuthorize("hasAuthority('STORE_READ') or hasAuthority('RETAIL_VIEW')")
    public ApiResponse<StoreResponse> getStore(@PathVariable UUID id) {
        return ApiResponse.of(stores.getStore(id));
    }

    @PostMapping("/stores")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('STORE_MANAGE') or hasAuthority('RETAIL_STORE_MANAGE')")
    public ApiResponse<StoreResponse> createStore(@Valid @RequestBody StoreRequest request) {
        return ApiResponse.of(stores.createStore(request));
    }

    @PutMapping("/stores/{id}")
    @PreAuthorize("hasAuthority('STORE_MANAGE') or hasAuthority('RETAIL_STORE_MANAGE')")
    public ApiResponse<StoreResponse> updateStore(@PathVariable UUID id, @Valid @RequestBody StoreRequest request) {
        return ApiResponse.of(stores.updateStore(id, request));
    }

    @DeleteMapping("/stores/{id}")
    @PreAuthorize("hasAuthority('STORE_MANAGE') or hasAuthority('RETAIL_ADMIN')")
    public ApiResponse<Void> deleteStore(@PathVariable UUID id) {
        stores.deleteStore(id);
        return ApiResponse.of(null);
    }

    @GetMapping("/terminals")
    @PreAuthorize("hasAuthority('RETAIL_VIEW') or hasAuthority('TERMINAL_MANAGE')")
    public ApiResponse<List<TerminalResponse>> listTerminals(@RequestParam(required = false) UUID storeId) {
        return ApiResponse.of(stores.listAllTerminals(storeId));
    }

    @GetMapping("/terminals/{id}")
    @PreAuthorize("hasAuthority('RETAIL_VIEW') or hasAuthority('TERMINAL_MANAGE')")
    public ApiResponse<TerminalResponse> getTerminal(@PathVariable UUID id) {
        return ApiResponse.of(stores.getTerminal(id));
    }

    @PostMapping("/terminals")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('TERMINAL_MANAGE') or hasAuthority('RETAIL_STORE_MANAGE')")
    public ApiResponse<TerminalResponse> createTerminal(
            @Valid @RequestBody com.flowledger.retail.dto.RetailDtos.TerminalRequest request) {
        return ApiResponse.of(stores.createTerminal(request));
    }

    @PutMapping("/terminals/{id}")
    @PreAuthorize("hasAuthority('TERMINAL_MANAGE') or hasAuthority('RETAIL_STORE_MANAGE')")
    public ApiResponse<TerminalResponse> updateTerminal(
            @PathVariable UUID id, @Valid @RequestBody com.flowledger.retail.dto.RetailDtos.TerminalRequest request) {
        return ApiResponse.of(stores.updateTerminal(id, request));
    }

    @DeleteMapping("/terminals/{id}")
    @PreAuthorize("hasAuthority('TERMINAL_MANAGE') or hasAuthority('RETAIL_ADMIN')")
    public ApiResponse<Void> deleteTerminal(@PathVariable UUID id) {
        stores.deleteTerminal(id);
        return ApiResponse.of(null);
    }

    @GetMapping("/cash-drawers")
    @PreAuthorize("hasAuthority('DRAWER_MANAGE') or hasAuthority('RETAIL_VIEW')")
    public ApiResponse<List<CashDrawerResponse>> listDrawers(@RequestParam(required = false) UUID terminalId) {
        return ApiResponse.of(drawers.list(terminalId));
    }

    @GetMapping("/cash-drawers/{id}")
    @PreAuthorize("hasAuthority('DRAWER_MANAGE') or hasAuthority('RETAIL_VIEW')")
    public ApiResponse<CashDrawerResponse> getDrawer(@PathVariable UUID id) {
        return ApiResponse.of(drawers.get(id));
    }

    @PostMapping("/cash-drawers")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('DRAWER_MANAGE') or hasAuthority('RETAIL_STORE_MANAGE')")
    public ApiResponse<CashDrawerResponse> createDrawer(@Valid @RequestBody CashDrawerRequest request) {
        return ApiResponse.of(drawers.create(request));
    }

    @PutMapping("/cash-drawers/{id}")
    @PreAuthorize("hasAuthority('DRAWER_MANAGE') or hasAuthority('RETAIL_STORE_MANAGE')")
    public ApiResponse<CashDrawerResponse> updateDrawer(
            @PathVariable UUID id, @Valid @RequestBody CashDrawerRequest request) {
        return ApiResponse.of(drawers.update(id, request));
    }

    @DeleteMapping("/cash-drawers/{id}")
    @PreAuthorize("hasAuthority('DRAWER_MANAGE') or hasAuthority('RETAIL_ADMIN')")
    public ApiResponse<Void> deleteDrawer(@PathVariable UUID id) {
        drawers.delete(id);
        return ApiResponse.of(null);
    }
}
