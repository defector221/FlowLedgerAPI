package com.flowledger.finance.currency.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public final class CurrencyDtos {
    private CurrencyDtos() {}

    public record CurrencyRequest(
            @NotBlank String code,
            @NotBlank String name,
            String symbol,
            Integer decimalPlaces,
            Boolean base,
            Boolean active) {}

    public record CurrencyResponse(
            UUID id, String code, String name, String symbol, int decimalPlaces, boolean base, boolean active) {}

    public record ExchangeRateRequest(
            @NotBlank String fromCurrency,
            @NotBlank String toCurrency,
            @NotNull @Positive BigDecimal rate,
            @NotNull LocalDate effectiveDate) {}

    public record ExchangeRateResponse(
            UUID id, String fromCurrency, String toCurrency, BigDecimal rate, LocalDate effectiveDate) {}
}
