package com.flowledger.tax.controller;

import com.flowledger.tax.dto.GstCalculationDtos.*;
import com.flowledger.tax.dto.TaxCalculationDtos.*;
import com.flowledger.tax.entity.TaxRule;
import com.flowledger.tax.service.GstCalculationService;
import com.flowledger.tax.service.TaxCalculationService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tax")
public class TaxCalculationController {
    private final GstCalculationService gstCalculationService;
    private final TaxCalculationService taxCalculationService;

    public TaxCalculationController(
            GstCalculationService gstCalculationService, TaxCalculationService taxCalculationService) {
        this.gstCalculationService = gstCalculationService;
        this.taxCalculationService = taxCalculationService;
    }

    @PostMapping("/calculate")
    public Response calculate(@Valid @RequestBody Request request) {
        return gstCalculationService.calculate(request);
    }

    @PostMapping("/calculate/line")
    @PreAuthorize("hasAuthority('TAX_READ')")
    public TaxResult calculateLine(
            @Valid @RequestBody LineTaxRequest line,
            @RequestParam String organizationStateCode,
            @RequestParam String placeOfSupplyStateCode,
            @RequestParam(required = false) String countryCode) {
        return taxCalculationService.calculateLineTax(
                line, organizationStateCode, placeOfSupplyStateCode, countryCode, null);
    }

    @PostMapping("/calculate/document")
    @PreAuthorize("hasAuthority('TAX_READ')")
    public TaxSummary calculateDocument(@Valid @RequestBody DocumentTaxRequest request) {
        return taxCalculationService.calculateDocumentTax(request);
    }

    @PostMapping("/calculate/rule-lookup")
    @PreAuthorize("hasAuthority('TAX_READ')")
    public TaxRule lookupRule(@Valid @RequestBody RuleLookupRequest request) {
        return taxCalculationService.findApplicableRule(request);
    }
}
