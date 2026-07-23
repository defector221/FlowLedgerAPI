package com.flowledger.tax.controller;

import com.flowledger.tax.dto.GstCalculationDtos.*;
import com.flowledger.tax.service.GstCalculationService;
import jakarta.validation.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tax")
public class GstCalculationController {
    private final GstCalculationService gstCalculationService;

    public GstCalculationController(GstCalculationService gstCalculationService) {
        this.gstCalculationService = gstCalculationService;
    }

    @PostMapping("/calculate")
    public Response calculate(@Valid @RequestBody Request request) {
        return gstCalculationService.calculate(request);
    }
}
