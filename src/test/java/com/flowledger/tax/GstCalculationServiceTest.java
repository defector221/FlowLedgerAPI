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
    void gstIntraStateSplitsCgstAndSgstFiftyFifty() {
        Response response = service.calculate(new Request(
                "27",
                "27",
                new BigDecimal("18"),
                false,
                BigDecimal.ONE,
                new BigDecimal("100"),
                BigDecimal.ZERO,
                "GST",
                "PLACE_OF_SUPPLY",
                new BigDecimal("50"),
                new BigDecimal("50")));
        assertEquals(new BigDecimal("100.00"), response.taxable());
        assertEquals(new BigDecimal("9.00"), response.cgst());
        assertEquals(new BigDecimal("9.00"), response.sgst());
        assertEquals(new BigDecimal("0"), response.igst());
        assertEquals(new BigDecimal("0"), response.otherTax());
        assertEquals(new BigDecimal("118.00"), response.lineTotal());
    }

    @Test
    void gstIntraStateUsesCustomShares() {
        Response response = service.calculate(new Request(
                "27",
                "27",
                new BigDecimal("18"),
                false,
                BigDecimal.ONE,
                new BigDecimal("100"),
                BigDecimal.ZERO,
                "GST",
                "PLACE_OF_SUPPLY",
                new BigDecimal("40"),
                new BigDecimal("60")));
        assertEquals(new BigDecimal("7.20"), response.cgst());
        assertEquals(new BigDecimal("10.80"), response.sgst());
        assertEquals(new BigDecimal("0"), response.igst());
    }

    @Test
    void gstInterStateAppliesIgstIgnoringShares() {
        Response response = service.calculate(new Request(
                "27",
                "29",
                new BigDecimal("18"),
                false,
                BigDecimal.ONE,
                new BigDecimal("100"),
                BigDecimal.ZERO,
                "GST",
                "PLACE_OF_SUPPLY",
                new BigDecimal("40"),
                new BigDecimal("60")));
        assertEquals(new BigDecimal("0"), response.cgst());
        assertEquals(new BigDecimal("0"), response.sgst());
        assertEquals(new BigDecimal("18.00"), response.igst());
        assertEquals(new BigDecimal("0"), response.otherTax());
    }

    @Test
    void customPercentIgnoresPlaceOfSupply() {
        Response response = service.calculate(new Request(
                "27",
                "29",
                new BigDecimal("18"),
                false,
                BigDecimal.ONE,
                new BigDecimal("100"),
                BigDecimal.ZERO,
                "GST",
                "CUSTOM_PERCENT",
                new BigDecimal("40"),
                new BigDecimal("60")));
        assertEquals(new BigDecimal("7.20"), response.cgst());
        assertEquals(new BigDecimal("10.80"), response.sgst());
        assertEquals(new BigDecimal("0"), response.igst());
    }

    @Test
    void igstNeverSplitsEvenIntraState() {
        Response response = service.calculate(new Request(
                "27",
                "27",
                new BigDecimal("18"),
                false,
                BigDecimal.ONE,
                new BigDecimal("100"),
                BigDecimal.ZERO,
                "IGST",
                "NO_SPLIT_IGST",
                BigDecimal.ZERO,
                BigDecimal.ZERO));
        assertEquals(new BigDecimal("0"), response.cgst());
        assertEquals(new BigDecimal("0"), response.sgst());
        assertEquals(new BigDecimal("18.00"), response.igst());
        assertEquals(new BigDecimal("0"), response.otherTax());
    }

    @Test
    void otherTaxAppliedAsMarkedWithoutGstSplit() {
        Response response = service.calculate(new Request(
                "27",
                "29",
                new BigDecimal("18"),
                false,
                BigDecimal.ONE,
                new BigDecimal("100"),
                BigDecimal.ZERO,
                "OTHER",
                "NO_SPLIT_OTHER",
                BigDecimal.ZERO,
                BigDecimal.ZERO));
        assertEquals(new BigDecimal("0"), response.cgst());
        assertEquals(new BigDecimal("0"), response.sgst());
        assertEquals(new BigDecimal("0"), response.igst());
        assertEquals(new BigDecimal("18.00"), response.otherTax());
        assertEquals(new BigDecimal("118.00"), response.lineTotal());
    }

    @Test
    void missingStateCodesDefaultToIntraStateSplit() {
        Response response = service.calculate(new Request(
                null,
                null,
                new BigDecimal("18"),
                false,
                BigDecimal.ONE,
                new BigDecimal("100"),
                BigDecimal.ZERO,
                "GST",
                "PLACE_OF_SUPPLY",
                new BigDecimal("50"),
                new BigDecimal("50")));
        assertEquals(new BigDecimal("9.00"), response.cgst());
        assertEquals(new BigDecimal("9.00"), response.sgst());
        assertEquals(new BigDecimal("0"), response.igst());
    }
}
