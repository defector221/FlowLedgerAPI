package com.flowledger.payment.controller;

import com.flowledger.payment.dto.PaymentDtos.Allocation;
import com.flowledger.payment.dto.PaymentDtos.PaymentRequest;
import com.flowledger.payment.entity.Payment;
import com.flowledger.payment.service.PaymentService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
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
    public Payment create(@Valid @RequestBody PaymentRequest request) {
        return service.create(request);
    }

    @PostMapping("/{id}/allocations")
    public Payment allocate(@PathVariable UUID id, @Valid @RequestBody Allocation allocation) {
        return service.allocate(id, allocation);
    }

    @GetMapping("/{id}")
    public Payment get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping
    public List<Payment> list() {
        return service.list();
    }

    @PostMapping("/{id}/cancel")
    public Payment cancel(@PathVariable UUID id) {
        return service.cancel(id);
    }
}
