package com.flowledger.purchase.controller;

import com.flowledger.common.dto.PageResponse;
import com.flowledger.purchase.dto.PurchaseDtos.*;
import com.flowledger.purchase.entity.*;
import com.flowledger.purchase.service.DebitNoteService;
import com.flowledger.purchase.service.GoodsReceiptService;
import com.flowledger.purchase.service.PurchaseInvoiceService;
import com.flowledger.purchase.service.PurchaseOrderService;
import com.flowledger.purchase.service.PurchaseReturnService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/purchases")
public class PurchaseController {
    private final PurchaseOrderService orders;
    private final GoodsReceiptService grns;
    private final PurchaseInvoiceService invoices;
    private final PurchaseReturnService returns;
    private final DebitNoteService debitNotes;

    public PurchaseController(
            PurchaseOrderService o,
            GoodsReceiptService g,
            PurchaseInvoiceService i,
            PurchaseReturnService r,
            DebitNoteService d) {
        orders = o;
        grns = g;
        invoices = i;
        returns = r;
        debitNotes = d;
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public PurchaseOrder createOrder(@Valid @RequestBody OrderRequest r) {
        return orders.create(r);
    }

    @GetMapping("/orders")
    public PageResponse<PurchaseOrder> orders(@PageableDefault(size = 20) Pageable pageable) {
        return orders.list(pageable);
    }

    @GetMapping("/orders/{id}")
    public PurchaseOrder order(@PathVariable UUID id) {
        return orders.get(id);
    }

    @GetMapping("/orders/{id}/fulfillment")
    public OrderFulfillment orderFulfillment(@PathVariable UUID id) {
        return invoices.fulfillment(id);
    }

    @PutMapping("/orders/{id}")
    public PurchaseOrder updateOrder(@PathVariable UUID id, @Valid @RequestBody OrderRequest r) {
        return orders.update(id, r);
    }

    @DeleteMapping("/orders/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteOrder(@PathVariable UUID id) {
        orders.delete(id);
    }

    @PostMapping("/orders/{id}/confirm")
    public PurchaseOrder confirmOrder(@PathVariable UUID id) {
        return orders.confirm(id);
    }

    @PostMapping("/orders/{id}/cancel")
    public PurchaseOrder cancelOrder(@PathVariable UUID id) {
        return orders.cancel(id);
    }

    @PostMapping("/grn/from-order/{poId}")
    @ResponseStatus(HttpStatus.CREATED)
    public GoodsReceipt fromOrder(@PathVariable UUID poId, @Valid @RequestBody GrnRequest r) {
        return grns.fromPurchaseOrder(poId, r);
    }

    @GetMapping("/grn/{id}")
    public GoodsReceipt grn(@PathVariable UUID id) {
        return grns.get(id);
    }

    @PutMapping("/grn/{id}")
    public GoodsReceipt updateGrn(@PathVariable UUID id, @Valid @RequestBody GrnRequest r) {
        return grns.update(id, r);
    }

    @PostMapping("/grn/{id}/confirm")
    public GoodsReceipt confirmGrn(@PathVariable UUID id) {
        return grns.confirm(id);
    }

    @PostMapping("/grn/{id}/cancel")
    public GoodsReceipt cancelGrn(@PathVariable UUID id) {
        return grns.cancel(id);
    }

    @GetMapping("/grn")
    public PageResponse<GoodsReceipt> grns(
            @PageableDefault(size = 20) Pageable pageable, @RequestParam(required = false) UUID purchaseOrderId) {
        return grns.list(pageable, purchaseOrderId);
    }

    @GetMapping("/grn/by-order/{poId}")
    public List<GoodsReceipt> grnsByOrder(@PathVariable UUID poId) {
        return grns.listByPurchaseOrder(poId);
    }

    @PostMapping("/invoices/from-order/{poId}")
    public PurchaseInvoice invoiceFromPo(@PathVariable UUID poId, @Valid @RequestBody InvoiceRequest r) {
        return invoices.fromPo(poId, r);
    }

    @PostMapping("/invoices/from-grn/{grnId}")
    public PurchaseInvoice invoiceFromGrn(@PathVariable UUID grnId, @Valid @RequestBody InvoiceRequest r) {
        return invoices.fromGrn(grnId, r);
    }

    @PutMapping("/invoices/{id}")
    public PurchaseInvoice updateInvoice(@PathVariable UUID id, @Valid @RequestBody InvoiceRequest r) {
        return invoices.update(id, r);
    }

    @PostMapping("/invoices/{id}/confirm")
    public PurchaseInvoice confirmInvoice(@PathVariable UUID id) {
        return invoices.confirm(id);
    }

    @PostMapping("/invoices/{id}/cancel")
    public PurchaseInvoice cancelInvoice(@PathVariable UUID id) {
        return invoices.cancel(id);
    }

    @GetMapping("/invoices/{id}")
    public PurchaseInvoice invoice(@PathVariable UUID id) {
        return invoices.get(id);
    }

    @GetMapping("/invoices")
    public PageResponse<PurchaseInvoice> invoices(
            @PageableDefault(size = 20) Pageable pageable,
            @RequestParam(required = false) UUID purchaseOrderId,
            @RequestParam(required = false) UUID goodsReceiptId) {
        return invoices.list(pageable, purchaseOrderId, goodsReceiptId);
    }

    @GetMapping("/invoices/by-order/{poId}")
    public List<PurchaseInvoice> invoicesByOrder(@PathVariable UUID poId) {
        return invoices.listByPurchaseOrder(poId);
    }

    @GetMapping("/invoices/by-grn/{grnId}")
    public List<PurchaseInvoice> invoicesByGrn(@PathVariable UUID grnId) {
        return invoices.listByGoodsReceipt(grnId);
    }

    @PostMapping("/returns")
    @ResponseStatus(HttpStatus.CREATED)
    public PurchaseReturn createReturn(@Valid @RequestBody ReturnRequest r) {
        return returns.create(r);
    }

    @GetMapping("/returns")
    public PageResponse<PurchaseReturn> returns(@PageableDefault(size = 20) Pageable pageable) {
        return returns.list(pageable);
    }

    @PostMapping("/returns/{id}/confirm")
    public PurchaseReturn confirmReturn(@PathVariable UUID id) {
        return returns.confirm(id);
    }

    @PostMapping("/debit-notes")
    @ResponseStatus(HttpStatus.CREATED)
    public DebitNote createDebitNote(@Valid @RequestBody DebitNoteRequest r) {
        return debitNotes.create(r);
    }

    @GetMapping("/debit-notes")
    public List<DebitNote> debitNotes() {
        return debitNotes.list();
    }
}
