package com.flowledger.finance.currency.controller;

import com.flowledger.common.dto.ApiResponse;
import com.flowledger.finance.currency.dto.CurrencyDtos.*;
import com.flowledger.finance.currency.service.CurrencyService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/currencies")
public class CurrencyController {
    private final CurrencyService service;

    public CurrencyController(CurrencyService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ORGANIZATION_ADMIN', 'ACCOUNTANT')")
    public ApiResponse<List<CurrencyResponse>> list() {
        return ApiResponse.of(service.listCurrencies());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ORGANIZATION_ADMIN')")
    public ApiResponse<CurrencyResponse> create(@Valid @RequestBody CurrencyRequest request) {
        return ApiResponse.of(service.createCurrency(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ORGANIZATION_ADMIN')")
    public ApiResponse<CurrencyResponse> update(@PathVariable UUID id, @Valid @RequestBody CurrencyRequest request) {
        return ApiResponse.of(service.updateCurrency(id, request));
    }

    @GetMapping("/exchange-rates")
    @PreAuthorize("hasAnyRole('ORGANIZATION_ADMIN', 'ACCOUNTANT')")
    public ApiResponse<List<ExchangeRateResponse>> listRates() {
        return ApiResponse.of(service.listRates());
    }

    @PostMapping("/exchange-rates")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ORGANIZATION_ADMIN', 'ACCOUNTANT')")
    public ApiResponse<ExchangeRateResponse> createRate(@Valid @RequestBody ExchangeRateRequest request) {
        return ApiResponse.of(service.createRate(request));
    }

    @PutMapping("/exchange-rates/{id}")
    @PreAuthorize("hasAnyRole('ORGANIZATION_ADMIN', 'ACCOUNTANT')")
    public ApiResponse<ExchangeRateResponse> updateRate(
            @PathVariable UUID id, @Valid @RequestBody ExchangeRateRequest request) {
        return ApiResponse.of(service.updateRate(id, request));
    }
}
