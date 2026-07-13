package com.flowledger.tax;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.flowledger.tax.dto.GstCalculationDtos.Request;
import com.flowledger.tax.dto.GstCalculationDtos.Response;
import com.flowledger.tax.service.GstCalculationService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class GstCalculationServiceTest {

    private final GstCalculationService service = new GstCalculationService();

    @Test
    void intraStateSplitsCgstAndSgst() {
        Response response = service.calculate(new Request(
                "27", "27", new BigDecimal("18"), false, BigDecimal.ONE, new BigDecimal("100"), BigDecimal.ZERO));
        assertEquals(new BigDecimal("100.00"), response.taxable());
        assertEquals(new BigDecimal("9.00"), response.cgst());
        assertEquals(new BigDecimal("9.00"), response.sgst());
        assertEquals(new BigDecimal("0"), response.igst());
        assertEquals(new BigDecimal("118.00"), response.lineTotal());
    }

    @Test
    void interStateAppliesIgst() {
        Response response = service.calculate(new Request(
                "27", "29", new BigDecimal("18"), false, BigDecimal.ONE, new BigDecimal("100"), BigDecimal.ZERO));
        assertEquals(new BigDecimal("0"), response.cgst());
        assertEquals(new BigDecimal("0"), response.sgst());
        assertEquals(new BigDecimal("18.00"), response.igst());
        assertEquals(new BigDecimal("118.00"), response.lineTotal());
    }

    @Test
    void taxInclusiveBackCalculatesTaxable() {
        Response response = service.calculate(new Request(
                "27", "27", new BigDecimal("18"), true, BigDecimal.ONE, new BigDecimal("118"), BigDecimal.ZERO));
        assertEquals(new BigDecimal("100.00"), response.taxable());
        assertEquals(new BigDecimal("118.00"), response.lineTotal());
    }
}
