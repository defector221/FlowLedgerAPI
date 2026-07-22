package com.flowledger.transport.controller;

import static com.flowledger.transport.dto.TransportDtos.*;

import com.flowledger.transport.service.TransportVehicleService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/transport/vehicles")
public class TransportVehicleController {
    private final TransportVehicleService service;
    public TransportVehicleController(TransportVehicleService service) { this.service = service; }
    @GetMapping @PreAuthorize("hasAuthority('TRANSPORT_VIEW')") public List<VehicleResponse> list() { return service.list(); }
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('TRANSPORT_VIEW')") public VehicleResponse get(@PathVariable UUID id) { return service.get(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('TRANSPORT_CREATE')") public VehicleResponse create(@Valid @RequestBody VehicleRequest r) { return service.create(r); }
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('TRANSPORT_EDIT')") public VehicleResponse update(@PathVariable UUID id, @Valid @RequestBody VehicleRequest r) { return service.update(id, r); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasAuthority('TRANSPORT_ADMIN')") public void delete(@PathVariable UUID id) { service.delete(id); }
}
