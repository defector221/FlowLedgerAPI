package com.flowledger.transport.controller;

import static com.flowledger.transport.dto.TransportDtos.*;

import com.flowledger.transport.domain.TransportEnums.ShipmentStatus;
import com.flowledger.transport.service.ShipmentService;
import com.flowledger.transport.service.TransportSourceAdapterService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/transport/shipments")
public class ShipmentController {
    private final ShipmentService service;
    private final TransportSourceAdapterService adapters;

    public ShipmentController(ShipmentService service, TransportSourceAdapterService adapters) {
        this.service = service;
        this.adapters = adapters;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('TRANSPORT_VIEW')")
    public Page<ShipmentResponse> list(
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable p) {
        return service.list(p);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('TRANSPORT_VIEW')")
    public ShipmentResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping("/{id}/timeline")
    @PreAuthorize("hasAuthority('TRANSPORT_VIEW')")
    public List<TimelineEvent> timeline(@PathVariable UUID id) {
        return service.timeline(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('TRANSPORT_CREATE')")
    public ShipmentResponse create(@Valid @RequestBody ShipmentRequest r) {
        return service.create(r);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('TRANSPORT_EDIT')")
    public ShipmentResponse update(@PathVariable UUID id, @Valid @RequestBody ShipmentRequest r) {
        return service.update(id, r);
    }

    @PostMapping("/from-challan/{challanId}")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('TRANSPORT_CREATE')")
    public ShipmentResponse fromChallan(
            @PathVariable UUID challanId, @Valid @RequestBody ChallanShipmentRequest r) {
        return service.createFromChallan(challanId, r.lines());
    }

    @PostMapping("/from-purchase-return/{returnId}")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('TRANSPORT_CREATE')")
    public ShipmentResponse fromPurchaseReturn(
            @PathVariable UUID returnId, @Valid @RequestBody ChallanShipmentRequest r) {
        return adapters.createFromPurchaseReturn(returnId, r.lines());
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('TRANSPORT_EDIT')")
    public ShipmentResponse submit(@PathVariable UUID id, @RequestBody(required = false) DecisionRequest r) {
        return service.submit(id, r);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('TRANSPORT_ADMIN')")
    public ShipmentResponse approve(@PathVariable UUID id, @RequestBody(required = false) DecisionRequest r) {
        return service.approve(id, r);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('TRANSPORT_ADMIN')")
    public ShipmentResponse reject(@PathVariable UUID id, @RequestBody(required = false) DecisionRequest r) {
        return service.reject(id, r);
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAuthority('TRANSPORT_ASSIGN')")
    public ShipmentResponse assign(@PathVariable UUID id, @Valid @RequestBody AssignmentRequest r) {
        return service.assign(id, r);
    }

    @PostMapping("/{id}/start-loading")
    @PreAuthorize("hasAuthority('TRANSPORT_EDIT')")
    public ShipmentResponse loading(@PathVariable UUID id, @RequestBody(required = false) TransitionRequest r) {
        return service.startLoading(id, r);
    }

    @PostMapping("/{id}/loaded")
    @PreAuthorize("hasAuthority('TRANSPORT_EDIT')")
    public ShipmentResponse loaded(@PathVariable UUID id, @RequestBody(required = false) TransitionRequest r) {
        return service.loaded(id, r);
    }

    @PostMapping("/{id}/dispatch")
    @PreAuthorize("hasAuthority('TRANSPORT_DISPATCH')")
    public ShipmentResponse dispatch(@PathVariable UUID id, @RequestBody(required = false) TransitionRequest r) {
        return service.dispatch(id, r);
    }

    @PostMapping("/{id}/checkpoint")
    @PreAuthorize("hasAuthority('TRANSPORT_TRACK')")
    public ShipmentResponse checkpoint(@PathVariable UUID id, @RequestBody(required = false) TransitionRequest r) {
        return service.checkpoint(id, r);
    }

    @PostMapping("/{id}/deliver")
    @PreAuthorize("hasAuthority('TRANSPORT_TRACK')")
    public ShipmentResponse deliver(@PathVariable UUID id, @RequestBody(required = false) TransitionRequest r) {
        return service.deliver(id, r);
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAuthority('TRANSPORT_CLOSE')")
    public ShipmentResponse close(@PathVariable UUID id, @RequestBody(required = false) TransitionRequest r) {
        return service.close(id, r);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('TRANSPORT_ADMIN')")
    public ShipmentResponse cancel(@PathVariable UUID id, @RequestBody(required = false) TransitionRequest r) {
        return service.cancel(id, r);
    }

    @GetMapping("/search")
    @PreAuthorize("hasAuthority('TRANSPORT_VIEW')")
    public Page<ShipmentResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String vehicleNumber,
            @RequestParam(required = false) String driverName,
            @RequestParam(required = false) String driverMobile,
            @RequestParam(required = false) String company,
            @RequestParam(required = false) String lrNumber,
            @RequestParam(required = false) String ewayBillNumber,
            @RequestParam(required = false) ShipmentStatus status,
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) String sourceDocumentType,
            @RequestParam(required = false) UUID sourceDocumentId,
            @RequestParam(required = false) String sku,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.search(
                new SearchRequest(
                        q,
                        vehicleNumber,
                        driverName,
                        driverMobile,
                        company,
                        lrNumber,
                        ewayBillNumber,
                        status,
                        fromDate,
                        toDate,
                        customerId,
                        warehouseId,
                        sourceDocumentType,
                        sourceDocumentId,
                        sku),
                pageable);
    }
}
