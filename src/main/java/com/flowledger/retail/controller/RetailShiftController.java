package com.flowledger.retail.controller;

import static com.flowledger.retail.dto.RetailDtos.*;

import com.flowledger.retail.service.RetailShiftService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/retail/shifts")
public class RetailShiftController {
    private final RetailShiftService service;

    public RetailShiftController(RetailShiftService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('RETAIL_VIEW')")
    public List<ShiftResponse> list(@RequestParam(required = false) UUID storeId) {
        return service.list(storeId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('RETAIL_VIEW')")
    public ShiftResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('RETAIL_SHIFT')")
    public ShiftResponse open(@Valid @RequestBody OpenShiftRequest r) {
        return service.open(r);
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAuthority('RETAIL_SHIFT')")
    public ShiftResponse close(@PathVariable UUID id, @Valid @RequestBody CloseShiftRequest r) {
        return service.close(id, r);
    }
}
