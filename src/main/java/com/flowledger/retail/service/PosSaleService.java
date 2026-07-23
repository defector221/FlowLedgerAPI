package com.flowledger.retail.service;

import static com.flowledger.retail.dto.RetailDtos.*;

import com.flowledger.common.tenant.TenantContext;
import com.flowledger.customer.entity.Customer;
import com.flowledger.customer.repository.CustomerRepository;
import com.flowledger.payment.dto.PaymentDtos;
import com.flowledger.payment.entity.Payment;
import com.flowledger.payment.service.PaymentService;
import com.flowledger.product.entity.Product;
import com.flowledger.product.repository.ProductRepository;
import com.flowledger.retail.domain.RetailEnums.PaymentMode;
import com.flowledger.retail.domain.RetailEnums.PosSaleStatus;
import com.flowledger.retail.entity.PosSale;
import com.flowledger.retail.entity.PosSaleLine;
import com.flowledger.retail.entity.PosSalePayment;
import com.flowledger.retail.entity.RetailStore;
import com.flowledger.retail.repository.PosSaleLineRepository;
import com.flowledger.retail.repository.PosSalePaymentRepository;
import com.flowledger.retail.repository.PosSaleRepository;
import com.flowledger.retail.repository.RetailStoreRepository;
import com.flowledger.sales.dto.SalesDtos;
import com.flowledger.sales.service.SalesInvoiceService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class PosSaleService {
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final RetailModuleGuard guard;
    private final PosSaleRepository sales;
    private final PosSaleLineRepository lines;
    private final PosSalePaymentRepository payments;
    private final RetailStoreRepository stores;
    private final ProductRepository products;
    private final CustomerRepository customers;
    private final SalesInvoiceService salesInvoiceService;
    private final PaymentService paymentService;

    public PosSaleService(
            RetailModuleGuard guard,
            PosSaleRepository sales,
            PosSaleLineRepository lines,
            PosSalePaymentRepository payments,
            RetailStoreRepository stores,
            ProductRepository products,
            CustomerRepository customers,
            SalesInvoiceService salesInvoiceService,
            PaymentService paymentService) {
        this.guard = guard;
        this.sales = sales;
        this.lines = lines;
        this.payments = payments;
        this.stores = stores;
        this.products = products;
        this.customers = customers;
        this.salesInvoiceService = salesInvoiceService;
        this.paymentService = paymentService;
    }

    @Transactional(readOnly = true)
    public List<PosSaleResponse> list(PosSaleStatus status) {
        List<PosSale> found = status == null
                ? sales.findByOrganizationIdAndDeletedFalseOrderByCreatedAtDesc(org())
                : sales.findByOrganizationIdAndStatusAndDeletedFalseOrderByCreatedAtDesc(org(), status);
        return found.stream().map(this::map).toList();
    }

    @Transactional(readOnly = true)
    public PosSaleResponse get(UUID id) {
        return map(load(id));
    }

    public PosSaleResponse createDraft(PosSaleRequest r) {
        PosSale e = new PosSale();
        e.setOrganizationId(org());
        e.setStoreId(r.storeId());
        e.setCounterId(r.counterId());
        e.setTerminalId(r.terminalId());
        e.setShiftId(r.shiftId());
        e.setCashierId(r.cashierId());
        e.setCustomerId(r.customerId());
        e.setStatus(PosSaleStatus.DRAFT);
        e.setNotes(r.notes());
        audit(e, true);
        return map(sales.save(e));
    }

    public PosSaleResponse addLine(UUID saleId, PosLineRequest r) {
        PosSale sale = loadEditable(saleId);
        List<PosSaleLine> existing = lines.findByOrganizationIdAndPosSaleIdOrderByLineOrderAsc(org(), saleId);
        PosSaleLine line = new PosSaleLine();
        line.setOrganizationId(org());
        line.setPosSaleId(saleId);
        line.setProductId(r.productId());
        line.setVariantId(r.variantId());
        line.setDescription(r.description());
        line.setBarcode(r.barcode());
        line.setQuantity(r.quantity());
        line.setRate(r.rate());
        line.setDiscountPercent(nz(r.discountPercent()));
        line.setTaxRate(nz(r.taxRate()));
        line.setLineOrder(existing.size());
        line.setLineTotal(lineTotal(line));
        audit(line, true);
        lines.save(line);
        recompute(sale);
        return map(sales.save(sale));
    }

    public PosSaleResponse removeLine(UUID saleId, UUID lineId) {
        PosSale sale = loadEditable(saleId);
        PosSaleLine line = lines.findById(lineId)
                .filter(l -> l.getPosSaleId().equals(saleId) && l.getOrganizationId().equals(org()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Line not found"));
        lines.delete(line);
        recompute(sale);
        return map(sales.save(sale));
    }

    public PosSaleResponse hold(UUID saleId, HoldRequest r) {
        PosSale sale = loadEditable(saleId);
        sale.setStatus(PosSaleStatus.HELD);
        sale.setHeldLabel(r.heldLabel());
        audit(sale, false);
        return map(sales.save(sale));
    }

    public PosSaleResponse resume(UUID saleId) {
        PosSale sale = load(saleId);
        if (sale.getStatus() != PosSaleStatus.HELD) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only held sales can be resumed");
        }
        sale.setStatus(PosSaleStatus.DRAFT);
        audit(sale, false);
        return map(sales.save(sale));
    }

    public PosSaleResponse voidSale(UUID saleId) {
        PosSale sale = loadEditable(saleId);
        sale.setStatus(PosSaleStatus.VOID);
        audit(sale, false);
        return map(sales.save(sale));
    }

    public PosSaleResponse checkout(UUID saleId, CheckoutRequest r) {
        PosSale sale = loadEditable(saleId);
        List<PosSaleLine> saleLines = lines.findByOrganizationIdAndPosSaleIdOrderByLineOrderAsc(org(), saleId);
        if (saleLines.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot checkout an empty cart");
        }
        recompute(sale);

        UUID customerId = resolveCustomer(r.customerId(), sale.getCustomerId());
        RetailStore store = stores.findByIdAndOrganizationIdAndDeletedFalse(sale.getStoreId(), org())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Store not found"));

        // Build and confirm a core sales invoice from the cart lines.
        List<SalesDtos.Item> items = new ArrayList<>();
        for (PosSaleLine line : saleLines) {
            Product product = products.findByIdAndOrganizationId(line.getProductId(), org())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "Product not found: " + line.getProductId()));
            items.add(new SalesDtos.Item(
                    line.getProductId(),
                    line.getDescription() == null ? product.getName() : line.getDescription(),
                    product.getHsnSacCode(),
                    line.getQuantity(),
                    product.getUnitId(),
                    line.getRate(),
                    line.getDiscountPercent(),
                    line.getTaxRate(),
                    null,
                    null,
                    null,
                    null));
        }
        SalesDtos.Invoice invoice = new SalesDtos.Invoice(
                customerId,
                LocalDate.now(),
                null,
                store.getWarehouseId(),
                null,
                null,
                null,
                null,
                null,
                false,
                null,
                null,
                null,
                sale.getNotes(),
                null,
                null,
                items);
        SalesDtos.InvoiceDetail draft = salesInvoiceService.createDraft(invoice);
        SalesDtos.InvoiceDetail confirmed = salesInvoiceService.confirm(draft.id());

        sale.setSalesInvoiceId(confirmed.id());
        sale.setCustomerId(customerId);
        if (r.receiptType() != null) {
            sale.setReceiptType(r.receiptType());
        }

        // Record POS payments; create RECEIPT payments with allocation for non-CREDIT modes.
        payments.deleteByPosSaleId(saleId);
        BigDecimal remaining = confirmed.grandTotal();
        for (PaymentInput input : r.payments()) {
            PosSalePayment posPayment = new PosSalePayment();
            posPayment.setOrganizationId(org());
            posPayment.setPosSaleId(saleId);
            posPayment.setPaymentMode(input.paymentMode());
            posPayment.setAmount(input.amount());
            posPayment.setReference(input.reference());

            if (input.paymentMode() != PaymentMode.CREDIT) {
                BigDecimal allocation = input.amount().min(remaining.max(BigDecimal.ZERO));
                List<PaymentDtos.Allocation> allocations = allocation.signum() > 0
                        ? List.of(new PaymentDtos.Allocation("SALES_INVOICE", confirmed.id(), allocation))
                        : List.of();
                Payment payment = paymentService.create(new PaymentDtos.PaymentRequest(
                        LocalDate.now(),
                        Payment.Type.RECEIPT,
                        Payment.Party.CUSTOMER,
                        customerId,
                        null,
                        input.amount(),
                        input.paymentMode().name(),
                        input.reference(),
                        null,
                        "POS sale " + saleId,
                        allocations));
                posPayment.setPaymentId(payment.getId());
                remaining = remaining.subtract(allocation);
            }
            audit(posPayment, true);
            payments.save(posPayment);
        }

        sale.setStatus(PosSaleStatus.COMPLETED);
        sale.setCompletedAt(OffsetDateTime.now());
        audit(sale, false);
        return map(sales.save(sale));
    }

    // ----------------------------------------------------------------- Helpers
    private UUID resolveCustomer(UUID requested, UUID existing) {
        if (requested != null) {
            return requested;
        }
        if (existing != null) {
            return existing;
        }
        return customers.findByOrganizationId(org()).stream()
                .filter(c -> !c.isArchived())
                .map(Customer::getId)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "No walk-in customer available; provide customerId in checkout request"));
    }

    private void recompute(PosSale sale) {
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal discountTotal = BigDecimal.ZERO;
        BigDecimal taxTotal = BigDecimal.ZERO;
        BigDecimal grandTotal = BigDecimal.ZERO;
        for (PosSaleLine line : lines.findByOrganizationIdAndPosSaleIdOrderByLineOrderAsc(org(), sale.getId())) {
            BigDecimal base = line.getQuantity().multiply(line.getRate());
            BigDecimal discount = base.multiply(nz(line.getDiscountPercent())).divide(HUNDRED, 2, RoundingMode.HALF_UP);
            BigDecimal taxable = base.subtract(discount);
            BigDecimal tax = taxable.multiply(nz(line.getTaxRate())).divide(HUNDRED, 2, RoundingMode.HALF_UP);
            subtotal = subtotal.add(base.setScale(2, RoundingMode.HALF_UP));
            discountTotal = discountTotal.add(discount);
            taxTotal = taxTotal.add(tax);
            grandTotal = grandTotal.add(taxable.add(tax).setScale(2, RoundingMode.HALF_UP));
        }
        sale.setSubtotal(subtotal);
        sale.setDiscountTotal(discountTotal);
        sale.setTaxTotal(taxTotal);
        sale.setGrandTotal(grandTotal);
    }

    private BigDecimal lineTotal(PosSaleLine line) {
        BigDecimal base = line.getQuantity().multiply(line.getRate());
        BigDecimal discount = base.multiply(nz(line.getDiscountPercent())).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        BigDecimal taxable = base.subtract(discount);
        BigDecimal tax = taxable.multiply(nz(line.getTaxRate())).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        return taxable.add(tax).setScale(2, RoundingMode.HALF_UP);
    }

    private PosSale load(UUID id) {
        return sales.findByIdAndOrganizationIdAndDeletedFalse(id, org())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "POS sale not found"));
    }

    private PosSale loadEditable(UUID id) {
        PosSale sale = load(id);
        if (sale.getStatus() == PosSaleStatus.COMPLETED || sale.getStatus() == PosSaleStatus.VOID) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "POS sale is not editable in status " + sale.getStatus());
        }
        return sale;
    }

    private PosSaleResponse map(PosSale e) {
        List<PosLineResponse> lineResponses = lines
                .findByOrganizationIdAndPosSaleIdOrderByLineOrderAsc(org(), e.getId()).stream()
                .map(l -> new PosLineResponse(
                        l.getId(),
                        l.getProductId(),
                        l.getVariantId(),
                        l.getDescription(),
                        l.getBarcode(),
                        l.getQuantity(),
                        l.getRate(),
                        l.getDiscountPercent(),
                        l.getTaxRate(),
                        l.getLineTotal(),
                        l.getLineOrder()))
                .toList();
        List<PosPaymentResponse> paymentResponses = payments
                .findByOrganizationIdAndPosSaleId(org(), e.getId()).stream()
                .map(p -> new PosPaymentResponse(
                        p.getId(), p.getPaymentMode(), p.getAmount(), p.getPaymentId(), p.getReference()))
                .toList();
        return new PosSaleResponse(
                e.getId(),
                e.getStoreId(),
                e.getCounterId(),
                e.getTerminalId(),
                e.getShiftId(),
                e.getCashierId(),
                e.getCustomerId(),
                e.getSalesInvoiceId(),
                e.getStatus(),
                e.getReceiptType(),
                e.getBillNumber(),
                e.getSubtotal(),
                e.getDiscountTotal(),
                e.getTaxTotal(),
                e.getGrandTotal(),
                e.getHeldLabel(),
                e.getNotes(),
                e.getCompletedAt(),
                lineResponses,
                paymentResponses,
                e.getVersion());
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private UUID org() {
        return guard.ensureEnabled();
    }

    private void audit(com.flowledger.common.entity.AuditedEntity e, boolean created) {
        TenantContext.userId().ifPresent(u -> {
            if (created) {
                e.setCreatedBy(u);
            }
            e.setUpdatedBy(u);
        });
    }
}
