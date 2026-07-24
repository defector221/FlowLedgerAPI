package com.flowledger.sales.controller;

import com.flowledger.common.dto.PageResponse;
import com.flowledger.sales.dto.SalesDtos.*;
import com.flowledger.sales.entity.*;
import com.flowledger.sales.service.SalesDocumentService;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/sales")
public class SalesDocumentController {

    private final SalesDocumentService service;

    public SalesDocumentController(SalesDocumentService service) {
        this.service = service;
    }

    // ── Quotations ──────────────────────────────────────────────────────────

    @GetMapping("/quotations")
    public PageResponse<Quotation> quotations(@PageableDefault(size = 20) Pageable pageable) {
        return service.listQuotations(pageable);
    }

    @GetMapping("/quotations/{id}")
    public Quotation quotation(@PathVariable UUID id) {
        return service.getQuotation(id);
    }

    @PostMapping("/quotations")
    @ResponseStatus(HttpStatus.CREATED)
    public Quotation createQuotation(@Valid @RequestBody QuotationRequest request) {
        return service.createQuotation(request);
    }

    @PutMapping("/quotations/{id}")
    public Quotation updateQuotation(@PathVariable UUID id, @Valid @RequestBody QuotationRequest request) {
        return service.updateQuotation(id, request);
    }

    @PostMapping("/quotations/{id}/cancel")
    public Quotation cancelQuotation(@PathVariable UUID id) {
        return service.cancelQuotation(id);
    }

    @PostMapping("/quotations/{id}/convert-to-order")
    public SalesOrder convertQuotation(@PathVariable UUID id) {
        return service.convertQuotationToOrder(id);
    }

    // ── Orders ──────────────────────────────────────────────────────────────

    @GetMapping("/orders")
    public PageResponse<SalesOrder> orders(@PageableDefault(size = 20) Pageable pageable) {
        return service.listOrders(pageable);
    }

    @GetMapping("/orders/{id}")
    public SalesOrder order(@PathVariable UUID id) {
        return service.getOrder(id);
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public SalesOrder createOrder(@Valid @RequestBody OrderRequest request) {
        return service.createOrder(request);
    }

    @PutMapping("/orders/{id}")
    public SalesOrder updateOrder(@PathVariable UUID id, @Valid @RequestBody OrderRequest request) {
        return service.updateOrder(id, request);
    }

    @PostMapping("/orders/{id}/cancel")
    public SalesOrder cancelOrder(@PathVariable UUID id) {
        return service.cancelOrder(id);
    }

    @PostMapping("/orders/{id}/convert-to-challan")
    public DeliveryChallan convertToChallan(@PathVariable UUID id, @RequestBody Map<String, UUID> body) {
        return service.convertOrderToChallan(id, body.get("warehouseId"));
    }

    @PostMapping("/orders/{id}/convert-to-invoice")
    public SalesInvoice convertOrderToInvoice(@PathVariable UUID id, @RequestBody Map<String, UUID> body) {
        return service.convertOrderToInvoice(id, body.get("warehouseId"));
    }

    // ── Challans ────────────────────────────────────────────────────────────

    @GetMapping("/challans")
    public PageResponse<DeliveryChallan> challans(@PageableDefault(size = 20) Pageable pageable) {
        return service.listChallans(pageable);
    }

    @GetMapping("/challans/{id}")
    public DeliveryChallan challan(@PathVariable UUID id) {
        return service.getChallan(id);
    }

    @PostMapping("/challans")
    @ResponseStatus(HttpStatus.CREATED)
    public DeliveryChallan createChallan(@Valid @RequestBody ChallanRequest request) {
        return service.createChallan(request);
    }

    @PutMapping("/challans/{id}")
    public DeliveryChallan updateChallan(@PathVariable UUID id, @Valid @RequestBody ChallanRequest request) {
        return service.updateChallan(id, request);
    }

    @PatchMapping("/challans/{id}/transport-required")
    public DeliveryChallan updateChallanTransportRequired(
            @PathVariable UUID id, @Valid @RequestBody TransportRequiredRequest request) {
        return service.updateChallanTransportRequired(id, request.transportRequired());
    }

    @PostMapping("/challans/{id}/cancel")
    public DeliveryChallan cancelChallan(@PathVariable UUID id) {
        return service.cancelChallan(id);
    }

    @GetMapping("/challans/{id}/invoice")
    public InvoiceDetail getChallanInvoice(@PathVariable UUID id) {
        return service.getInvoiceForChallan(id);
    }

    @PostMapping("/challans/{id}/convert-to-invoice")
    public SalesInvoice convertChallanToInvoice(@PathVariable UUID id) {
        return service.convertChallanToInvoice(id);
    }

    // ── Returns ─────────────────────────────────────────────────────────────

    @GetMapping("/returns")
    public PageResponse<SalesReturn> returns(@PageableDefault(size = 20) Pageable pageable) {
        return service.listReturns(pageable);
    }

    @GetMapping("/returns/{id}")
    public SalesReturn getReturn(@PathVariable UUID id) {
        return service.getReturn(id);
    }

    @PostMapping("/returns")
    @ResponseStatus(HttpStatus.CREATED)
    public SalesReturn createReturn(@Valid @RequestBody ReturnRequest request) {
        return service.createReturn(request);
    }

    @PostMapping("/returns/{id}/confirm")
    public SalesReturn confirmReturn(@PathVariable UUID id) {
        return service.confirmReturn(id);
    }

    // ── Credit notes ────────────────────────────────────────────────────────

    @GetMapping("/credit-notes")
    public PageResponse<CreditNote> creditNotes(@PageableDefault(size = 20) Pageable pageable) {
        return service.listCreditNotes(pageable);
    }

    @GetMapping("/credit-notes/{id}")
    public CreditNote creditNote(@PathVariable UUID id) {
        return service.getCreditNote(id);
    }

    @PostMapping("/credit-notes")
    @ResponseStatus(HttpStatus.CREATED)
    public CreditNote createCreditNote(@Valid @RequestBody CreditNoteRequest request) {
        return service.createCreditNote(request);
    }
}
