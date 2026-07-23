package com.flowledger.retail.controller;

import static com.flowledger.retail.dto.RetailDtos.*;

import com.flowledger.retail.domain.RetailEnums.PosSaleStatus;
import com.flowledger.retail.service.RetailReturnService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/retail/returns")
public class RetailReturnController {
    private final RetailReturnService service;

    public RetailReturnController(RetailReturnService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('RETAIL_VIEW')")
    public List<ReturnResponse> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('RETAIL_VIEW')")
    public ReturnResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('RETAIL_POS')")
    public ReturnResponse create(@Valid @RequestBody ReturnRequest r) {
        return service.create(r);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('RETAIL_STORE_MANAGE')")
    public ReturnResponse updateStatus(@PathVariable UUID id, @RequestParam PosSaleStatus status) {
        return service.updateStatus(id, status);
    }
}
