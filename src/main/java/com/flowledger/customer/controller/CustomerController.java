package com.flowledger.customer.controller;

import com.flowledger.customer.dto.CustomerDtos.*;
import com.flowledger.customer.service.CustomerService;
import jakarta.validation.Valid;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.math.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {
    private final CustomerService service;

    public CustomerController(CustomerService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Response create(@Valid @RequestBody Create dto) {
        return service.create(dto);
    }

    @GetMapping("/{id}")
    public Response get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    public Response update(@PathVariable UUID id, @Valid @RequestBody Update dto) {
        return service.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@PathVariable UUID id) {
        service.archive(id);
    }

    @GetMapping
    public Page<Response> search(@RequestParam(required = false) String search, @RequestParam(required = false) Boolean archived, Pageable pageable) {
        return service.search(new Search(search, archived), pageable);
    }

    @GetMapping("/{id}/statement")
    public Statement statement(@PathVariable UUID id) {
        return service.statement(id);
    }

    @GetMapping("/{id}/outstanding")
    public BigDecimal outstanding(@PathVariable UUID id) {
        return service.outstanding(id);
    }

    @GetMapping("/{id}/payments")
    public List<Object> payments(@PathVariable UUID id) {
        service.get(id);
        return List.of();
    }
}
