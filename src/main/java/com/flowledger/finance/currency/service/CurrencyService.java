package com.flowledger.finance.currency.service;

import com.flowledger.common.exception.BusinessException;
import com.flowledger.common.exception.ConflictException;
import com.flowledger.common.exception.ResourceNotFoundException;
import com.flowledger.common.tenant.TenantContext;
import com.flowledger.finance.currency.dto.CurrencyDtos.*;
import com.flowledger.finance.currency.entity.Currency;
import com.flowledger.finance.currency.entity.ExchangeRate;
import com.flowledger.finance.currency.repository.CurrencyRepository;
import com.flowledger.finance.currency.repository.ExchangeRateRepository;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CurrencyService {
    private final CurrencyRepository currencies;
    private final ExchangeRateRepository rates;

    public CurrencyService(CurrencyRepository currencies, ExchangeRateRepository rates) {
        this.currencies = currencies;
        this.rates = rates;
    }

    @Transactional(readOnly = true)
    public List<CurrencyResponse> listCurrencies() {
        return currencies.findByOrganizationIdOrderByCodeAsc(TenantContext.getOrganizationId()).stream()
                .map(CurrencyService::toCurrency)
                .toList();
    }

    @Transactional
    public CurrencyResponse createCurrency(CurrencyRequest request) {
        UUID org = TenantContext.getOrganizationId();
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (currencies.existsByOrganizationIdAndCode(org, code)) {
            throw new ConflictException("Currency code already exists");
        }
        if (Boolean.TRUE.equals(request.base())) {
            currencies.clearBase(org);
        }
        Currency entity = new Currency();
        entity.setOrganizationId(org);
        apply(entity, request, code);
        return toCurrency(currencies.save(entity));
    }

    @Transactional
    public CurrencyResponse updateCurrency(UUID id, CurrencyRequest request) {
        Currency entity = currencies
                .findByIdAndOrganizationId(id, TenantContext.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Currency not found: " + id));
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (!code.equalsIgnoreCase(entity.getCode())
                && currencies.existsByOrganizationIdAndCode(entity.getOrganizationId(), code)) {
            throw new ConflictException("Currency code already exists");
        }
        if (Boolean.TRUE.equals(request.base())) {
            currencies.clearBase(entity.getOrganizationId());
        }
        apply(entity, request, code);
        return toCurrency(currencies.save(entity));
    }

    @Transactional(readOnly = true)
    public List<ExchangeRateResponse> listRates() {
        return rates.findByOrganizationIdOrderByEffectiveDateDesc(TenantContext.getOrganizationId()).stream()
                .map(CurrencyService::toRate)
                .toList();
    }

    @Transactional
    public ExchangeRateResponse createRate(ExchangeRateRequest request) {
        UUID org = TenantContext.getOrganizationId();
        String from = request.fromCurrency().trim().toUpperCase(Locale.ROOT);
        String to = request.toCurrency().trim().toUpperCase(Locale.ROOT);
        if (from.equals(to)) {
            throw new BusinessException("From and to currency must differ");
        }
        if (rates.existsByOrganizationIdAndFromCurrencyAndToCurrencyAndEffectiveDate(
                org, from, to, request.effectiveDate())) {
            throw new ConflictException("Exchange rate already exists for that date");
        }
        ExchangeRate entity = new ExchangeRate();
        entity.setOrganizationId(org);
        entity.setFromCurrency(from);
        entity.setToCurrency(to);
        entity.setRate(request.rate());
        entity.setEffectiveDate(request.effectiveDate());
        return toRate(rates.save(entity));
    }

    @Transactional
    public ExchangeRateResponse updateRate(UUID id, ExchangeRateRequest request) {
        ExchangeRate entity = rates.findByIdAndOrganizationId(id, TenantContext.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Exchange rate not found: " + id));
        String from = request.fromCurrency().trim().toUpperCase(Locale.ROOT);
        String to = request.toCurrency().trim().toUpperCase(Locale.ROOT);
        if (from.equals(to)) {
            throw new BusinessException("From and to currency must differ");
        }
        entity.setFromCurrency(from);
        entity.setToCurrency(to);
        entity.setRate(request.rate());
        entity.setEffectiveDate(request.effectiveDate());
        return toRate(rates.save(entity));
    }

    private static void apply(Currency entity, CurrencyRequest request, String code) {
        entity.setCode(code);
        entity.setName(request.name().trim());
        entity.setSymbol(request.symbol());
        if (request.decimalPlaces() != null) {
            entity.setDecimalPlaces(request.decimalPlaces());
        }
        if (request.base() != null) {
            entity.setBase(request.base());
        }
        if (request.active() != null) {
            entity.setActive(request.active());
        }
    }

    private static CurrencyResponse toCurrency(Currency c) {
        return new CurrencyResponse(
                c.getId(), c.getCode(), c.getName(), c.getSymbol(), c.getDecimalPlaces(), c.isBase(), c.isActive());
    }

    private static ExchangeRateResponse toRate(ExchangeRate r) {
        return new ExchangeRateResponse(
                r.getId(), r.getFromCurrency(), r.getToCurrency(), r.getRate(), r.getEffectiveDate());
    }
}
