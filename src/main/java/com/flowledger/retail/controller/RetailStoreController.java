package com.flowledger.retail.controller;

import static com.flowledger.retail.dto.RetailDtos.*;

import com.flowledger.retail.service.RetailStoreService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/retail")
public class RetailStoreController {
    private final RetailStoreService service;

    public RetailStoreController(RetailStoreService service) {
        this.service = service;
    }

    // ------------------------------------------------------------- Store types
    @GetMapping("/store-types")
    @PreAuthorize("hasAuthority('RETAIL_VIEW')")
    public List<StoreTypeResponse> listStoreTypes() {
        return service.listStoreTypes();
    }

    @PostMapping("/store-types")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('RETAIL_STORE_MANAGE')")
    public StoreTypeResponse createStoreType(@Valid @RequestBody StoreTypeRequest r) {
        return service.createStoreType(r);
    }

    @PutMapping("/store-types/{id}")
    @PreAuthorize("hasAuthority('RETAIL_STORE_MANAGE')")
    public StoreTypeResponse updateStoreType(@PathVariable UUID id, @Valid @RequestBody StoreTypeRequest r) {
        return service.updateStoreType(id, r);
    }

    @DeleteMapping("/store-types/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('RETAIL_ADMIN')")
    public void deleteStoreType(@PathVariable UUID id) {
        service.deleteStoreType(id);
    }

    // ------------------------------------------------------------------ Stores
    @GetMapping("/stores")
    @PreAuthorize("hasAuthority('RETAIL_VIEW')")
    public List<StoreResponse> listStores() {
        return service.listStores();
    }

    @GetMapping("/stores/{id}")
    @PreAuthorize("hasAuthority('RETAIL_VIEW')")
    public StoreResponse getStore(@PathVariable UUID id) {
        return service.getStore(id);
    }

    @PostMapping("/stores")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('RETAIL_STORE_MANAGE')")
    public StoreResponse createStore(@Valid @RequestBody StoreRequest r) {
        return service.createStore(r);
    }

    @PutMapping("/stores/{id}")
    @PreAuthorize("hasAuthority('RETAIL_STORE_MANAGE')")
    public StoreResponse updateStore(@PathVariable UUID id, @Valid @RequestBody StoreRequest r) {
        return service.updateStore(id, r);
    }

    @DeleteMapping("/stores/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('RETAIL_ADMIN')")
    public void deleteStore(@PathVariable UUID id) {
        service.deleteStore(id);
    }

    // ---------------------------------------------------------------- Counters
    @GetMapping("/counters")
    @PreAuthorize("hasAuthority('RETAIL_VIEW')")
    public List<CounterResponse> listCounters(@RequestParam UUID storeId) {
        return service.listCounters(storeId);
    }

    @PostMapping("/counters")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('RETAIL_STORE_MANAGE')")
    public CounterResponse createCounter(@Valid @RequestBody CounterRequest r) {
        return service.createCounter(r);
    }

    @PutMapping("/counters/{id}")
    @PreAuthorize("hasAuthority('RETAIL_STORE_MANAGE')")
    public CounterResponse updateCounter(@PathVariable UUID id, @Valid @RequestBody CounterRequest r) {
        return service.updateCounter(id, r);
    }

    @DeleteMapping("/counters/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('RETAIL_ADMIN')")
    public void deleteCounter(@PathVariable UUID id) {
        service.deleteCounter(id);
    }

    // --------------------------------------------------------------- Terminals
    @GetMapping("/terminals")
    @PreAuthorize("hasAuthority('RETAIL_VIEW')")
    public List<TerminalResponse> listTerminals(@RequestParam UUID storeId) {
        return service.listTerminals(storeId);
    }

    @PostMapping("/terminals")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('RETAIL_STORE_MANAGE')")
    public TerminalResponse createTerminal(@Valid @RequestBody TerminalRequest r) {
        return service.createTerminal(r);
    }

    @PutMapping("/terminals/{id}")
    @PreAuthorize("hasAuthority('RETAIL_STORE_MANAGE')")
    public TerminalResponse updateTerminal(@PathVariable UUID id, @Valid @RequestBody TerminalRequest r) {
        return service.updateTerminal(id, r);
    }

    @DeleteMapping("/terminals/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('RETAIL_ADMIN')")
    public void deleteTerminal(@PathVariable UUID id) {
        service.deleteTerminal(id);
    }

    // ---------------------------------------------------------------- Cashiers
    @GetMapping("/cashiers")
    @PreAuthorize("hasAuthority('RETAIL_VIEW')")
    public List<CashierResponse> listCashiers(@RequestParam UUID storeId) {
        return service.listCashiers(storeId);
    }

    @PostMapping("/cashiers")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('RETAIL_STORE_MANAGE')")
    public CashierResponse createCashier(@Valid @RequestBody CashierRequest r) {
        return service.createCashier(r);
    }

    @PutMapping("/cashiers/{id}")
    @PreAuthorize("hasAuthority('RETAIL_STORE_MANAGE')")
    public CashierResponse updateCashier(@PathVariable UUID id, @Valid @RequestBody CashierRequest r) {
        return service.updateCashier(id, r);
    }

    @DeleteMapping("/cashiers/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('RETAIL_ADMIN')")
    public void deleteCashier(@PathVariable UUID id) {
        service.deleteCashier(id);
    }
}
