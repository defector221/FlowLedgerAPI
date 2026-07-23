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
import java.util.Objects;
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
    private final RetailLoyaltyService loyaltyService;
    private final RetailPricingService pricingService;

    public PosSaleService(
            RetailModuleGuard guard,
            PosSaleRepository sales,
            PosSaleLineRepository lines,
            PosSalePaymentRepository payments,
            RetailStoreRepository stores,
            ProductRepository products,
            CustomerRepository customers,
            SalesInvoiceService salesInvoiceService,
            PaymentService paymentService,
            RetailLoyaltyService loyaltyService,
            RetailPricingService pricingService) {
        this.guard = guard;
        this.sales = sales;
        this.lines = lines;
        this.payments = payments;
        this.stores = stores;
        this.products = products;
        this.customers = customers;
        this.salesInvoiceService = salesInvoiceService;
        this.paymentService = paymentService;
        this.loyaltyService = loyaltyService;
        this.pricingService = pricingService;
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
        BigDecimal discountPercent = nz(r.discountPercent());
        BigDecimal taxRate = nz(r.taxRate());

        // Same SKU / variant / price → bump qty instead of a duplicate bill line.
        PosSaleLine mergeTarget = existing.stream()
                .filter(line -> Objects.equals(line.getProductId(), r.productId())
                        && Objects.equals(line.getVariantId(), r.variantId())
                        && line.getRate().compareTo(r.rate()) == 0
                        && nz(line.getDiscountPercent()).compareTo(discountPercent) == 0
                        && nz(line.getTaxRate()).compareTo(taxRate) == 0)
                .findFirst()
                .orElse(null);

        if (mergeTarget != null) {
            mergeTarget.setQuantity(mergeTarget.getQuantity().add(r.quantity()));
            if (r.description() != null && !r.description().isBlank()) {
                mergeTarget.setDescription(r.description());
            }
            if (r.barcode() != null && !r.barcode().isBlank()) {
                mergeTarget.setBarcode(r.barcode());
            }
            mergeTarget.setLineTotal(lineTotal(mergeTarget));
            audit(mergeTarget, false);
            lines.save(mergeTarget);
            recompute(sale);
            return map(sales.save(sale));
        }

        PosSaleLine line = new PosSaleLine();
        line.setOrganizationId(org());
        line.setPosSaleId(saleId);
        line.setProductId(r.productId());
        line.setVariantId(r.variantId());
        line.setDescription(r.description());
        line.setBarcode(r.barcode());
        line.setQuantity(r.quantity());
        line.setRate(r.rate());
        line.setDiscountPercent(discountPercent);
        line.setTaxRate(taxRate);
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
                .filter(l ->
                        l.getPosSaleId().equals(saleId) && l.getOrganizationId().equals(org()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Line not found"));
        lines.delete(line);
        recompute(sale);
        return map(sales.save(sale));
    }

    public PosSaleResponse updateLine(UUID saleId, UUID lineId, PosLineUpdateRequest r) {
        PosSale sale = loadEditable(saleId);
        PosSaleLine line = lines.findById(lineId)
                .filter(l ->
                        l.getPosSaleId().equals(saleId) && l.getOrganizationId().equals(org()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Line not found"));
        if (r.quantity() != null) {
            line.setQuantity(r.quantity());
        }
        if (r.discountPercent() != null) {
            if (r.discountPercent().signum() < 0 || r.discountPercent().compareTo(HUNDRED) > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Discount percent must be 0–100");
            }
            line.setDiscountPercent(r.discountPercent());
        }
        if (r.rate() != null) {
            line.setRate(r.rate());
        }
        if (r.taxRate() != null) {
            line.setTaxRate(r.taxRate());
        }
        line.setLineTotal(lineTotal(line));
        audit(line, false);
        lines.save(line);
        recompute(sale);
        return map(sales.save(sale));
    }

    public PosSaleResponse applyAdjustments(UUID saleId, PosAdjustmentsRequest r) {
        PosSale sale = loadEditable(saleId);
        if (r.customerId() != null) {
            customers
                    .findByIdAndOrganizationId(r.customerId(), org())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Customer not found"));
            sale.setCustomerId(r.customerId());
        }
        if (r.billDiscountPercent() != null) {
            if (r.billDiscountPercent().signum() < 0 || r.billDiscountPercent().compareTo(HUNDRED) > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bill discount percent must be 0–100");
            }
            sale.setBillDiscountPercent(r.billDiscountPercent());
        }
        if (r.billDiscountAmount() != null) {
            if (r.billDiscountAmount().signum() < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bill discount amount cannot be negative");
            }
            sale.setBillDiscountAmount(r.billDiscountAmount());
        }
        if (r.loyaltyPointsRedeemed() != null) {
            if (r.loyaltyPointsRedeemed().signum() < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Loyalty points cannot be negative");
            }
            UUID customerForLoyalty = r.customerId() != null ? r.customerId() : sale.getCustomerId();
            if (r.loyaltyPointsRedeemed().signum() > 0) {
                if (customerForLoyalty == null) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "Select a customer to redeem loyalty points");
                }
                var account = loyaltyService.getOrCreateAccount(new LoyaltyAccountRequest(customerForLoyalty, null));
                if (account.pointsBalance().compareTo(r.loyaltyPointsRedeemed()) < 0) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient loyalty points");
                }
            }
            sale.setLoyaltyPointsRedeemed(r.loyaltyPointsRedeemed());
        }
        if (r.couponCode() != null) {
            String code = r.couponCode().isBlank() ? null : r.couponCode().trim();
            sale.setCouponCode(code);
            if (code != null) {
                // Preview coupon against current line net; store as bill amount when coupon wins.
                BigDecimal billBase = previewLineNet(saleId);
                ApplyCouponResponse applied = pricingService.applyCoupon(new ApplyCouponRequest(code, billBase));
                if (applied.applied()) {
                    sale.setBillDiscountAmount(nz(applied.discountAmount()));
                    sale.setBillDiscountPercent(BigDecimal.ZERO);
                } else {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            applied.message() == null ? "Coupon not applicable" : applied.message());
                }
            }
        }
        audit(sale, false);
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
        sale.setCustomerId(customerId);
        RetailStore store = stores.findByIdAndOrganizationIdAndDeletedFalse(sale.getStoreId(), org())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Store not found"));

        BigDecimal loyaltyRedeem = nz(sale.getLoyaltyPointsRedeemed());
        if (loyaltyRedeem.signum() > 0) {
            loyaltyService.redeem(
                    new RedeemRequest(customerId, loyaltyRedeem, "POS_SALE", saleId, "Redeemed at POS checkout"));
        }

        // Distribute bill + loyalty discount into line discount % so the sales invoice matches POS totals.
        BigDecimal lineNetBeforeBill = previewLineNet(saleId);
        BigDecimal headerDisc = headerDiscountAmount(sale, lineNetBeforeBill);
        List<SalesDtos.Item> items = new ArrayList<>();
        for (PosSaleLine line : saleLines) {
            Product product = products.findByIdAndOrganizationId(line.getProductId(), org())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "Product not found: " + line.getProductId()));
            BigDecimal base = line.getQuantity().multiply(line.getRate());
            BigDecimal lineDiscAmt =
                    base.multiply(nz(line.getDiscountPercent())).divide(HUNDRED, 4, RoundingMode.HALF_UP);
            BigDecimal share = BigDecimal.ZERO;
            if (lineNetBeforeBill.signum() > 0 && headerDisc.signum() > 0 && base.signum() > 0) {
                BigDecimal lineNet = base.subtract(lineDiscAmt);
                share = headerDisc.multiply(lineNet).divide(lineNetBeforeBill, 4, RoundingMode.HALF_UP);
            }
            BigDecimal effectiveDiscPct = base.signum() <= 0
                    ? nz(line.getDiscountPercent())
                    : lineDiscAmt.add(share).multiply(HUNDRED).divide(base, 4, RoundingMode.HALF_UP);
            if (effectiveDiscPct.compareTo(HUNDRED) > 0) {
                effectiveDiscPct = HUNDRED;
            }
            items.add(new SalesDtos.Item(
                    line.getProductId(),
                    line.getDescription() == null ? product.getName() : line.getDescription(),
                    product.getHsnSacCode(),
                    line.getQuantity(),
                    product.getUnitId(),
                    line.getRate(),
                    effectiveDiscPct,
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
        if (r.receiptType() != null) {
            sale.setReceiptType(r.receiptType());
        }

        // Record POS payments; create RECEIPT payments with allocation for non-CREDIT modes.
        payments.deleteByPosSaleId(saleId);
        BigDecimal remaining = sale.getGrandTotal();
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

        // Earn loyalty on net paid amount (1 currency × tier earn rate).
        if (sale.getGrandTotal().signum() > 0) {
            try {
                var account = loyaltyService.getOrCreateAccount(new LoyaltyAccountRequest(customerId, null));
                BigDecimal earnRate = BigDecimal.ONE;
                if (account.tierId() != null) {
                    earnRate = loyaltyService.listTiers().stream()
                            .filter(t -> t.id().equals(account.tierId()))
                            .map(TierResponse::earnRate)
                            .findFirst()
                            .orElse(BigDecimal.ONE);
                }
                BigDecimal earned = sale.getGrandTotal().multiply(nz(earnRate)).setScale(0, RoundingMode.DOWN);
                if (earned.signum() > 0) {
                    loyaltyService.earn(
                            new EarnRequest(customerId, earned, "POS_SALE", saleId, "Earned at POS checkout"));
                }
            } catch (RuntimeException ignored) {
                // Loyalty earn is best-effort; checkout should not fail if tiers are misconfigured.
            }
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
        BigDecimal lineDiscountTotal = BigDecimal.ZERO;
        BigDecimal taxTotal = BigDecimal.ZERO;
        BigDecimal lineGrand = BigDecimal.ZERO;
        for (PosSaleLine line : lines.findByOrganizationIdAndPosSaleIdOrderByLineOrderAsc(org(), sale.getId())) {
            BigDecimal base = line.getQuantity().multiply(line.getRate());
            BigDecimal discount = base.multiply(nz(line.getDiscountPercent())).divide(HUNDRED, 2, RoundingMode.HALF_UP);
            BigDecimal taxable = base.subtract(discount);
            BigDecimal tax = taxable.multiply(nz(line.getTaxRate())).divide(HUNDRED, 2, RoundingMode.HALF_UP);
            BigDecimal total = taxable.add(tax).setScale(2, RoundingMode.HALF_UP);
            line.setLineTotal(total);
            lines.save(line);
            subtotal = subtotal.add(base.setScale(2, RoundingMode.HALF_UP));
            lineDiscountTotal = lineDiscountTotal.add(discount);
            taxTotal = taxTotal.add(tax);
            lineGrand = lineGrand.add(total);
        }
        BigDecimal afterLine = subtotal.subtract(lineDiscountTotal).max(BigDecimal.ZERO);
        BigDecimal headerDisc = headerDiscountAmount(sale, afterLine);
        // Prefer reducing payable after tax so cashiers see an intuitive bill total.
        if (headerDisc.compareTo(lineGrand) > 0) {
            headerDisc = lineGrand;
            // Clamp loyalty/bill amounts so stored values stay consistent with payable.
            BigDecimal pctPart =
                    afterLine.multiply(nz(sale.getBillDiscountPercent())).divide(HUNDRED, 2, RoundingMode.HALF_UP);
            BigDecimal remaining = headerDisc.subtract(pctPart).max(BigDecimal.ZERO);
            BigDecimal billAmt = nz(sale.getBillDiscountAmount()).min(remaining);
            sale.setBillDiscountAmount(billAmt);
            remaining = remaining.subtract(billAmt);
            sale.setLoyaltyPointsRedeemed(nz(sale.getLoyaltyPointsRedeemed()).min(remaining));
            headerDisc = headerDiscountAmount(sale, afterLine).min(lineGrand);
        }
        sale.setSubtotal(subtotal);
        sale.setDiscountTotal(lineDiscountTotal.add(headerDisc).setScale(2, RoundingMode.HALF_UP));
        sale.setTaxTotal(taxTotal);
        sale.setGrandTotal(lineGrand.subtract(headerDisc).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
    }

    private BigDecimal headerDiscountAmount(PosSale sale, BigDecimal afterLineDiscountBase) {
        BigDecimal pct = afterLineDiscountBase
                .multiply(nz(sale.getBillDiscountPercent()))
                .divide(HUNDRED, 2, RoundingMode.HALF_UP);
        return pct.add(nz(sale.getBillDiscountAmount()))
                .add(nz(sale.getLoyaltyPointsRedeemed()))
                .max(BigDecimal.ZERO);
    }

    private BigDecimal previewLineNet(UUID saleId) {
        BigDecimal net = BigDecimal.ZERO;
        for (PosSaleLine line : lines.findByOrganizationIdAndPosSaleIdOrderByLineOrderAsc(org(), saleId)) {
            BigDecimal base = line.getQuantity().multiply(line.getRate());
            BigDecimal discount = base.multiply(nz(line.getDiscountPercent())).divide(HUNDRED, 2, RoundingMode.HALF_UP);
            net = net.add(base.subtract(discount));
        }
        return net.max(BigDecimal.ZERO);
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
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "POS sale is not editable in status " + sale.getStatus());
        }
        return sale;
    }

    private PosSaleResponse map(PosSale e) {
        List<PosLineResponse> lineResponses =
                lines.findByOrganizationIdAndPosSaleIdOrderByLineOrderAsc(org(), e.getId()).stream()
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
        List<PosPaymentResponse> paymentResponses = payments.findByOrganizationIdAndPosSaleId(org(), e.getId()).stream()
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
                nz(e.getBillDiscountPercent()),
                nz(e.getBillDiscountAmount()),
                nz(e.getLoyaltyPointsRedeemed()),
                e.getCouponCode(),
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
