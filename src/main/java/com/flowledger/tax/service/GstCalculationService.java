package com.flowledger.tax.service;

import com.flowledger.tax.dto.GstCalculationDtos.*;
import java.math.*;
import org.springframework.stereotype.*;

@Service
public class GstCalculationService {
    private static final int SCALE = 2;

    public Response calculate(Request r) {
        BigDecimal gross =
                r.quantity().multiply(r.rate()).subtract(r.discount() == null ? BigDecimal.ZERO : r.discount());
        BigDecimal hundred = BigDecimal.valueOf(100);
        BigDecimal taxable = Boolean.TRUE.equals(r.taxInclusive())
                ? gross.multiply(hundred).divide(hundred.add(r.taxRate()), SCALE, RoundingMode.HALF_UP)
                : gross.setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal tax = taxable.multiply(r.taxRate()).divide(hundred, SCALE, RoundingMode.HALF_UP);
        boolean intra = r.organizationStateCode()
                .trim()
                .equalsIgnoreCase(r.placeOfSupplyStateCode().trim());
        BigDecimal cgst = intra ? tax.divide(BigDecimal.valueOf(2), SCALE, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal sgst = intra ? tax.subtract(cgst) : BigDecimal.ZERO;
        BigDecimal igst = intra ? BigDecimal.ZERO : tax;
        BigDecimal total =
                Boolean.TRUE.equals(r.taxInclusive()) ? gross.setScale(SCALE, RoundingMode.HALF_UP) : taxable.add(tax);
        return new Response(taxable, cgst, sgst, igst, total);
    }
}
