package com.flowledger.transport.controller;

import static com.flowledger.transport.dto.TransportDtos.*;

import com.flowledger.transport.service.ShipmentLegService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/transport")
public class ShipmentLegController {
    private final ShipmentLegService legs;

    public ShipmentLegController(ShipmentLegService legs) {
        this.legs = legs;
    }

    @GetMapping("/shipments/{shipmentId}/legs")
    @PreAuthorize("hasAuthority('TRANSPORT_VIEW')")
    public List<LegResponse> list(@PathVariable UUID shipmentId) {
        return legs.list(shipmentId);
    }

    @PostMapping("/shipments/{shipmentId}/legs")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('TRANSPORT_EDIT')")
    public LegResponse add(@PathVariable UUID shipmentId, @Valid @RequestBody LegRequest request) {
        return legs.add(shipmentId, request);
    }

    @PutMapping("/legs/{id}")
    @PreAuthorize("hasAuthority('TRANSPORT_EDIT')")
    public LegResponse update(@PathVariable UUID id, @Valid @RequestBody LegRequest request) {
        return legs.update(id, request);
    }

    @DeleteMapping("/legs/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('TRANSPORT_EDIT')")
    public void delete(@PathVariable UUID id) {
        legs.delete(id);
    }

    @PatchMapping("/legs/{id}/status")
    @PreAuthorize("hasAuthority('TRANSPORT_EDIT')")
    public LegResponse status(@PathVariable UUID id, @Valid @RequestBody LegStatusRequest request) {
        return legs.setStatus(id, request);
    }

    @PatchMapping("/legs/{id}/location")
    @PreAuthorize("hasAuthority('TRANSPORT_TRACK')")
    public LegResponse location(@PathVariable UUID id, @Valid @RequestBody LegLocationRequest request) {
        return legs.updateLocation(id, request);
    }

    @PatchMapping("/legs/{id}/dispatch")
    @PreAuthorize("hasAuthority('TRANSPORT_DISPATCH')")
    public LegResponse dispatch(@PathVariable UUID id, @RequestBody(required = false) TransitionRequest request) {
        return legs.dispatch(id, request);
    }

    @PatchMapping("/legs/{id}/arrive")
    @PreAuthorize("hasAuthority('TRANSPORT_TRACK')")
    public LegResponse arrive(@PathVariable UUID id, @RequestBody(required = false) TransitionRequest request) {
        return legs.arrive(id, request);
    }

    @PatchMapping("/legs/{id}/complete")
    @PreAuthorize("hasAuthority('TRANSPORT_TRACK')")
    public LegResponse complete(@PathVariable UUID id, @RequestBody(required = false) TransitionRequest request) {
        return legs.complete(id, request);
    }

    @GetMapping("/legs/{id}/locations")
    @PreAuthorize("hasAuthority('TRANSPORT_VIEW')")
    public List<LegLocationResponse> locations(@PathVariable UUID id) {
        return legs.locationHistory(id);
    }

    @GetMapping("/legs/{id}/documents")
    @PreAuthorize("hasAuthority('TRANSPORT_VIEW')")
    public List<LegDocumentResponse> documents(@PathVariable UUID id) {
        return legs.listDocuments(id);
    }

    @PostMapping("/legs/{id}/documents")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('TRANSPORT_EDIT')")
    public LegDocumentResponse addDocument(@PathVariable UUID id, @Valid @RequestBody LegDocumentRequest request) {
        return legs.addDocument(id, request);
    }
}
