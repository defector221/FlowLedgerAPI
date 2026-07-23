package com.flowledger.accounting.service;

import com.flowledger.accounting.domain.FiscalYearStatus;
import com.flowledger.accounting.domain.PeriodStatus;
import com.flowledger.accounting.dto.AccountingDtos.FiscalYearRequest;
import com.flowledger.accounting.dto.AccountingDtos.FiscalYearResponse;
import com.flowledger.accounting.dto.AccountingDtos.PeriodResponse;
import com.flowledger.accounting.entity.AccountingPeriod;
import com.flowledger.accounting.entity.FiscalYear;
import com.flowledger.accounting.repository.AccountingPeriodRepository;
import com.flowledger.accounting.repository.FiscalYearRepository;
import com.flowledger.common.exception.BusinessException;
import com.flowledger.common.exception.ConflictException;
import com.flowledger.common.exception.ResourceNotFoundException;
import com.flowledger.common.tenant.TenantContext;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FiscalYearService {
    private final FiscalYearRepository fiscalYears;
    private final AccountingPeriodRepository periods;

    public FiscalYearService(FiscalYearRepository fiscalYears, AccountingPeriodRepository periods) {
        this.fiscalYears = fiscalYears;
        this.periods = periods;
    }

    @Transactional(readOnly = true)
    public List<FiscalYearResponse> list() {
        return fiscalYears.findByOrganizationIdOrderByStartDateDesc(TenantContext.getOrganizationId()).stream()
                .map(FiscalYearService::toResponse)
                .toList();
    }

    @Transactional
    public FiscalYearResponse create(FiscalYearRequest request) {
        UUID org = TenantContext.getOrganizationId();
        if (!request.endDate().isAfter(request.startDate())) {
            throw new BusinessException("End date must be after start date");
        }
        if (fiscalYears.existsOverlapping(org, request.startDate(), request.endDate(), null)) {
            throw new ConflictException("Fiscal year overlaps with an existing fiscal year");
        }
        FiscalYear fiscalYear = new FiscalYear();
        fiscalYear.setOrganizationId(org);
        fiscalYear.setName(request.name());
        fiscalYear.setStartDate(request.startDate());
        fiscalYear.setEndDate(request.endDate());
        fiscalYear.setStatus(FiscalYearStatus.OPEN);
        fiscalYear.setCurrent(
                fiscalYears.findByOrganizationIdAndCurrentTrue(org).isEmpty());
        FiscalYear saved = fiscalYears.save(fiscalYear);
        createMonthlyPeriods(org, saved);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<PeriodResponse> periods(UUID fiscalYearId) {
        FiscalYear fiscalYear = fiscalYears
                .findByIdAndOrganizationId(fiscalYearId, TenantContext.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Fiscal year not found: " + fiscalYearId));
        return periods.findByFiscalYearIdOrderByPeriodNumberAsc(fiscalYear.getId()).stream()
                .map(FiscalYearService::toResponse)
                .toList();
    }

    private void createMonthlyPeriods(UUID organizationId, FiscalYear fiscalYear) {
        LocalDate cursor = fiscalYear.getStartDate();
        int number = 1;
        while (!cursor.isAfter(fiscalYear.getEndDate())) {
            LocalDate periodEnd = cursor.plusMonths(1).minusDays(1);
            if (periodEnd.isAfter(fiscalYear.getEndDate())) {
                periodEnd = fiscalYear.getEndDate();
            }
            AccountingPeriod period = new AccountingPeriod();
            period.setOrganizationId(organizationId);
            period.setFiscalYearId(fiscalYear.getId());
            period.setPeriodNumber(number++);
            period.setName(cursor.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + cursor.getYear());
            period.setStartDate(cursor);
            period.setEndDate(periodEnd);
            period.setStatus(PeriodStatus.OPEN);
            periods.save(period);
            cursor = cursor.plusMonths(1);
        }
    }

    private static FiscalYearResponse toResponse(FiscalYear fiscalYear) {
        return new FiscalYearResponse(
                fiscalYear.getId(),
                fiscalYear.getName(),
                fiscalYear.getStartDate(),
                fiscalYear.getEndDate(),
                fiscalYear.getStatus(),
                fiscalYear.isCurrent());
    }

    private static PeriodResponse toResponse(AccountingPeriod period) {
        return new PeriodResponse(
                period.getId(),
                period.getFiscalYearId(),
                period.getPeriodNumber(),
                period.getName(),
                period.getStartDate(),
                period.getEndDate(),
                period.getStatus());
    }
}
