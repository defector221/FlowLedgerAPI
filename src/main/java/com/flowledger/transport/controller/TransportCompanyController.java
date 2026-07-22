package com.flowledger.transport.controller;

import static com.flowledger.transport.dto.TransportDtos.*;

import com.flowledger.transport.service.TransportCompanyService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/transport/companies")
public class TransportCompanyController {
    private final TransportCompanyService service;
    public TransportCompanyController(TransportCompanyService service) { this.service = service; }
    @GetMapping @PreAuthorize("hasAuthority('TRANSPORT_VIEW')") public List<CompanyResponse> list() { return service.list(); }
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('TRANSPORT_VIEW')") public CompanyResponse get(@PathVariable UUID id) { return service.get(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('TRANSPORT_CREATE')") public CompanyResponse create(@Valid @RequestBody CompanyRequest r) { return service.create(r); }
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('TRANSPORT_EDIT')") public CompanyResponse update(@PathVariable UUID id, @Valid @RequestBody CompanyRequest r) { return service.update(id, r); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasAuthority('TRANSPORT_ADMIN')") public void delete(@PathVariable UUID id) { service.delete(id); }
}
