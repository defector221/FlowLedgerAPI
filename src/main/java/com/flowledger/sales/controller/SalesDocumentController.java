package com.flowledger.sales.controller;

import com.flowledger.sales.entity.DeliveryChallan;
import com.flowledger.sales.entity.Quotation;
import com.flowledger.sales.entity.SalesInvoice;
import com.flowledger.sales.entity.SalesOrder;
import com.flowledger.sales.service.SalesDocumentService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sales")
public class SalesDocumentController {

    private final SalesDocumentService service;

    public SalesDocumentController(SalesDocumentService service) {
        this.service = service;
    }

    @GetMapping("/quotations")
    public List<Quotation> quotations() {
        return service.listQuotations();
    }

    @GetMapping("/quotations/{id}")
    public Quotation quotation(@PathVariable UUID id) {
        return service.getQuotation(id);
    }

    @PostMapping("/quotations/{id}/convert-to-order")
    public SalesOrder convertQuotation(@PathVariable UUID id) {
        return service.convertQuotationToOrder(id);
    }

    @PostMapping("/orders/{id}/convert-to-challan")
    public DeliveryChallan convertToChallan(@PathVariable UUID id, @RequestBody Map<String, UUID> body) {
        return service.convertOrderToChallan(id, body.get("warehouseId"));
    }

    @PostMapping("/orders/{id}/convert-to-invoice")
    public SalesInvoice convertOrderToInvoice(@PathVariable UUID id, @RequestBody Map<String, UUID> body) {
        return service.convertOrderToInvoice(id, body.get("warehouseId"));
    }

    @PostMapping("/challans/{id}/convert-to-invoice")
    public SalesInvoice convertChallanToInvoice(@PathVariable UUID id) {
        return service.convertChallanToInvoice(id);
    }
}
