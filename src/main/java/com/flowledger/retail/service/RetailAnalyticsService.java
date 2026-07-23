package com.flowledger.retail.service;

import static com.flowledger.retail.dto.RetailDtos.*;

import com.flowledger.retail.domain.RetailEnums.PosSaleStatus;
import com.flowledger.retail.entity.PosSale;
import com.flowledger.retail.repository.PosSaleRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RetailAnalyticsService {
    private final RetailModuleGuard guard;
    private final PosSaleRepository sales;

    public RetailAnalyticsService(RetailModuleGuard guard, PosSaleRepository sales) {
        this.guard = guard;
        this.sales = sales;
    }

    public DailySalesResponse dailySales(UUID storeId, LocalDate date) {
        UUID org = guard.ensureEnabled();
        LocalDate day = date == null ? LocalDate.now() : date;
        OffsetDateTime from = day.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime to = day.atTime(LocalTime.MAX).atOffset(ZoneOffset.UTC);

        List<PosSale> completed = sales.findByOrganizationIdAndStoreIdAndStatusAndCompletedAtBetweenAndDeletedFalse(
                org, storeId, PosSaleStatus.COMPLETED, from, to);

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal discountTotal = BigDecimal.ZERO;
        BigDecimal taxTotal = BigDecimal.ZERO;
        BigDecimal grandTotal = BigDecimal.ZERO;
        for (PosSale sale : completed) {
            subtotal = subtotal.add(nz(sale.getSubtotal()));
            discountTotal = discountTotal.add(nz(sale.getDiscountTotal()));
            taxTotal = taxTotal.add(nz(sale.getTaxTotal()));
            grandTotal = grandTotal.add(nz(sale.getGrandTotal()));
        }

        return new DailySalesResponse(day, completed.size(), subtotal, discountTotal, taxTotal, grandTotal);
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
