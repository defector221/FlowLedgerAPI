package com.flowledger.payment.controller;

import com.flowledger.payment.dto.PaymentDtos.Allocation;
import com.flowledger.payment.dto.PaymentDtos.PaymentRequest;
import com.flowledger.payment.dto.PaymentDtos.PaymentResponse;
import com.flowledger.payment.entity.Payment;
import com.flowledger.payment.service.PaymentService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {
    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse create(@Valid @RequestBody PaymentRequest request) {
        return PaymentResponse.from(service.create(request));
    }

    @PostMapping("/{id}/allocations")
    public PaymentResponse allocate(@PathVariable UUID id, @Valid @RequestBody List<@Valid Allocation> allocations) {
        return PaymentResponse.from(service.allocate(id, allocations));
    }

    @GetMapping("/{id}")
    public PaymentResponse get(@PathVariable UUID id) {
        return PaymentResponse.from(service.get(id));
    }

    @GetMapping
    public List<PaymentResponse> list(
            @RequestParam(required = false) Payment.Type type,
            @RequestParam(required = false) Payment.Party partyType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) UUID supplierId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String search) {
        return service.list(type, partyType, status, customerId, supplierId, from, to, search).stream()
                .map(PaymentResponse::from)
                .toList();
    }

    @PostMapping("/{id}/cancel")
    public PaymentResponse cancel(@PathVariable UUID id) {
        return PaymentResponse.from(service.cancel(id));
    }
}
