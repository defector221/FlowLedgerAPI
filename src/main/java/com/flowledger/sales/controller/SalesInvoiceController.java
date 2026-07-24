package com.flowledger.sales.controller;

import com.flowledger.common.dto.PageResponse;
import com.flowledger.sales.dto.SalesDtos.*;
import com.flowledger.sales.entity.SalesInvoice;
import com.flowledger.sales.service.SalesInvoiceService;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/sales/invoices")
public class SalesInvoiceController {
    private final SalesInvoiceService service;

    public SalesInvoiceController(SalesInvoiceService s) {
        service = s;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InvoiceDetail create(@Valid @RequestBody Invoice d) {
        return service.createDraft(d);
    }

    @PutMapping("/{id}")
    public InvoiceDetail update(@PathVariable UUID id, @Valid @RequestBody Invoice d) {
        return service.updateDraft(id, d);
    }

    @PostMapping("/{id}/confirm")
    public InvoiceDetail confirm(@PathVariable UUID id) {
        return service.confirm(id);
    }

    @PostMapping("/{id}/cancel")
    public InvoiceDetail cancel(@PathVariable UUID id) {
        return service.cancel(id);
    }

    @GetMapping("/{id}")
    public InvoiceDetail get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping
    public PageResponse<SalesInvoice> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID customerId,
            @PageableDefault(size = 20, sort = "invoiceDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.list(status, customerId, pageable);
    }
}
