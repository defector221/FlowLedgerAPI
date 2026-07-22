package com.flowledger.transport.controller;

import static com.flowledger.transport.dto.TransportDtos.*;

import com.flowledger.transport.service.TransportDriverService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/transport/drivers")
public class TransportDriverController {
    private final TransportDriverService service;
    public TransportDriverController(TransportDriverService service) { this.service = service; }
    @GetMapping @PreAuthorize("hasAuthority('TRANSPORT_VIEW')") public List<DriverResponse> list() { return service.list(); }
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('TRANSPORT_VIEW')") public DriverResponse get(@PathVariable UUID id) { return service.get(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('TRANSPORT_CREATE')") public DriverResponse create(@Valid @RequestBody DriverRequest r) { return service.create(r); }
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('TRANSPORT_EDIT')") public DriverResponse update(@PathVariable UUID id, @Valid @RequestBody DriverRequest r) { return service.update(id, r); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasAuthority('TRANSPORT_ADMIN')") public void delete(@PathVariable UUID id) { service.delete(id); }
}
