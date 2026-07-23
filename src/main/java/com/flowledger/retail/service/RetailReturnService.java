package com.flowledger.retail.service;

import static com.flowledger.retail.dto.RetailDtos.*;

import com.flowledger.common.tenant.TenantContext;
import com.flowledger.retail.domain.RetailEnums.PosSaleStatus;
import com.flowledger.retail.domain.RetailEnums.RefundMode;
import com.flowledger.retail.entity.PosReturn;
import com.flowledger.retail.entity.PosReturnLine;
import com.flowledger.retail.entity.PosSale;
import com.flowledger.retail.entity.RetailStoreCredit;
import com.flowledger.retail.repository.PosReturnLineRepository;
import com.flowledger.retail.repository.PosReturnRepository;
import com.flowledger.retail.repository.PosSaleRepository;
import com.flowledger.retail.repository.RetailStoreCreditRepository;
import com.flowledger.retail.repository.RetailStoreRepository;
import com.flowledger.sales.dto.SalesDtos;
import com.flowledger.sales.entity.SalesReturn;
import com.flowledger.sales.service.SalesDocumentService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class RetailReturnService {
    private final RetailModuleGuard guard;
    private final PosReturnRepository returns;
    private final PosReturnLineRepository lines;
    private final RetailStoreCreditRepository storeCredits;
    private final PosSaleRepository posSales;
    private final RetailStoreRepository stores;
    private final SalesDocumentService salesDocumentService;

    public RetailReturnService(
            RetailModuleGuard guard,
            PosReturnRepository returns,
            PosReturnLineRepository lines,
            RetailStoreCreditRepository storeCredits,
            PosSaleRepository posSales,
            RetailStoreRepository stores,
            SalesDocumentService salesDocumentService) {
        this.guard = guard;
        this.returns = returns;
        this.lines = lines;
        this.storeCredits = storeCredits;
        this.posSales = posSales;
        this.stores = stores;
        this.salesDocumentService = salesDocumentService;
    }

    @Transactional(readOnly = true)
    public List<ReturnResponse> list() {
        return returns.findByOrganizationIdAndDeletedFalseOrderByCreatedAtDesc(org()).stream()
                .map(this::map)
                .toList();
    }

    @Transactional(readOnly = true)
    public ReturnResponse get(UUID id) {
        return map(load(id));
    }

    public ReturnResponse create(ReturnRequest r) {
        stores.findByIdAndOrganizationIdAndDeletedFalse(r.storeId(), org())
                .orElseThrow(() -> notFound("Store not found"));

        PosSale originalSale = null;
        if (r.originalPosSaleId() != null) {
            originalSale = posSales
                    .findByIdAndOrganizationIdAndDeletedFalse(r.originalPosSaleId(), org())
                    .orElseThrow(() -> notFound("Original POS sale not found"));
        }

        UUID invoiceId = r.originalInvoiceId();
        if (invoiceId == null && originalSale != null) {
            invoiceId = originalSale.getSalesInvoiceId();
        }

        PosReturn e = new PosReturn();
        e.setOrganizationId(org());
        e.setStoreId(r.storeId());
        e.setOriginalPosSaleId(r.originalPosSaleId());
        e.setOriginalInvoiceId(invoiceId);
        e.setStatus(PosSaleStatus.DRAFT);
        e.setReason(r.reason());
        e.setRefundMode(r.refundMode() == null ? RefundMode.REFUND : r.refundMode());
        e.setNotes(r.notes());
        e.setTotalAmount(BigDecimal.ZERO);
        audit(e, true);
        e = returns.save(e);

        BigDecimal total = BigDecimal.ZERO;
        List<SalesDtos.ReturnItem> salesItems = new ArrayList<>();
        for (ReturnLineRequest lineReq : r.lines()) {
            PosReturnLine line = new PosReturnLine();
            line.setOrganizationId(org());
            line.setPosReturnId(e.getId());
            line.setProductId(lineReq.productId());
            line.setQuantity(lineReq.quantity());
            line.setRate(lineReq.rate());
            line.setLineTotal(lineReq.quantity().multiply(lineReq.rate()).setScale(2, RoundingMode.HALF_UP));
            audit(line, true);
            lines.save(line);
            total = total.add(line.getLineTotal());
            salesItems.add(new SalesDtos.ReturnItem(lineReq.productId(), lineReq.quantity(), lineReq.rate()));
        }
        e.setTotalAmount(total);

        if (invoiceId != null) {
            // Link to sales return so inventory posting happens when that document is confirmed.
            SalesReturn salesReturn = salesDocumentService.createReturn(new SalesDtos.ReturnRequest(
                    invoiceId,
                    LocalDate.now(),
                    appendNote(r.notes(), "POS return " + e.getId() + "; inventory posted when sales return confirmed"),
                    salesItems));
            e.setSalesReturnId(salesReturn.getId());
            e.setNotes(appendNote(
                    e.getNotes(), "Linked sales return " + salesReturn.getId() + "; inventory handled when linked"));
        } else {
            e.setNotes(appendNote(
                    e.getNotes(), "No invoice linked; inventory handled when linked to a sales return"));
        }

        audit(e, false);
        return map(returns.save(e));
    }

    public ReturnResponse updateStatus(UUID id, PosSaleStatus status) {
        PosReturn e = load(id);
        if (status == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status is required");
        }
        PosSaleStatus previous = e.getStatus();
        e.setStatus(status);
        audit(e, false);

        if (status == PosSaleStatus.COMPLETED
                && previous != PosSaleStatus.COMPLETED
                && e.getRefundMode() == RefundMode.STORE_CREDIT) {
            postStoreCredit(e);
        }

        return map(returns.save(e));
    }

    private void postStoreCredit(PosReturn e) {
        UUID customerId = resolveCustomerId(e);
        if (customerId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Store credit requires a customer on the original sale");
        }
        RetailStoreCredit credit = storeCredits
                .findByOrganizationIdAndCustomerId(org(), customerId)
                .orElseGet(() -> {
                    RetailStoreCredit c = new RetailStoreCredit();
                    c.setOrganizationId(org());
                    c.setCustomerId(customerId);
                    c.setBalance(BigDecimal.ZERO);
                    audit(c, true);
                    return c;
                });
        credit.setBalance(credit.getBalance().add(e.getTotalAmount()));
        audit(credit, false);
        storeCredits.save(credit);
        e.setNotes(appendNote(e.getNotes(), "Store credit " + e.getTotalAmount() + " posted for customer " + customerId));
    }

    private UUID resolveCustomerId(PosReturn e) {
        if (e.getOriginalPosSaleId() == null) {
            return null;
        }
        return posSales
                .findByIdAndOrganizationIdAndDeletedFalse(e.getOriginalPosSaleId(), org())
                .map(PosSale::getCustomerId)
                .orElse(null);
    }

    private String appendNote(String existing, String addition) {
        if (existing == null || existing.isBlank()) {
            return addition;
        }
        return existing + " | " + addition;
    }

    private PosReturn load(UUID id) {
        return returns
                .findByIdAndOrganizationIdAndDeletedFalse(id, org())
                .orElseThrow(() -> notFound("Return not found"));
    }

    private ReturnResponse map(PosReturn e) {
        List<ReturnLineResponse> lineResponses = lines
                .findByOrganizationIdAndPosReturnId(org(), e.getId())
                .stream()
                .map(l -> new ReturnLineResponse(
                        l.getId(), l.getProductId(), l.getQuantity(), l.getRate(), l.getLineTotal()))
                .toList();
        return new ReturnResponse(
                e.getId(),
                e.getStoreId(),
                e.getOriginalPosSaleId(),
                e.getOriginalInvoiceId(),
                e.getSalesReturnId(),
                e.getStatus(),
                e.getReason(),
                e.getRefundMode(),
                e.getNotes(),
                e.getTotalAmount(),
                lineResponses,
                e.getVersion());
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

    private ResponseStatusException notFound(String m) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, m);
    }
}
