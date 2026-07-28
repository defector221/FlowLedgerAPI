package com.flowledger.tax.service;

import com.flowledger.tax.dto.GstCalculationDtos.Request;
import com.flowledger.tax.dto.GstCalculationDtos.Response;
import org.springframework.stereotype.Service;

@Service
public class GstCalculationService {
    private final TaxLineCalculator taxLineCalculator;

    public GstCalculationService(TaxLineCalculator taxLineCalculator) {
        this.taxLineCalculator = taxLineCalculator;
    }

    public Response calculate(Request request) {
        return taxLineCalculator.calculate(request);
    }
}
