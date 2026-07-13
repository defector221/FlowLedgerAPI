package com.flowledger.purchase.service;

import com.flowledger.common.tenant.TenantContext;
import com.flowledger.common.util.DocumentNumberService;
import com.flowledger.organization.entity.Organization;
import com.flowledger.organization.repository.OrganizationRepository;
import com.flowledger.purchase.dto.PurchaseDtos.DebitNoteRequest;
import com.flowledger.purchase.entity.DebitNote;
import com.flowledger.purchase.entity.PurchaseInvoice;
import com.flowledger.purchase.entity.PurchaseReturn;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class DebitNoteService {
    @PersistenceContext
    private EntityManager em;

    private final PurchaseReturnService returns;
    private final PurchaseInvoiceService invoices;
    private final DocumentNumberService numbers;
    private final OrganizationRepository organizations;

    public DebitNoteService(
            PurchaseReturnService returns,
            PurchaseInvoiceService invoices,
            DocumentNumberService numbers,
            OrganizationRepository organizations) {
        this.returns = returns;
        this.invoices = invoices;
        this.numbers = numbers;
        this.organizations = organizations;
    }

    public DebitNote create(DebitNoteRequest request) {
        if (request.purchaseReturnId() == null && request.purchaseInvoiceId() == null)
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "purchaseReturnId or purchaseInvoiceId is required");
        UUID supplierId = request.supplierId();
        BigDecimal amount = request.amount();
        if (request.purchaseReturnId() != null) {
            PurchaseReturn pr = returns.get(request.purchaseReturnId());
            if (supplierId == null) supplierId = pr.getSupplierId();
            if (amount == null) amount = pr.getGrandTotal();
        }
        if (request.purchaseInvoiceId() != null) {
            PurchaseInvoice invoice = invoices.get(request.purchaseInvoiceId());
            if (supplierId == null) supplierId = invoice.getSupplierId();
            if (amount == null) amount = invoice.getGrandTotal();
        }
        if (supplierId == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "supplierId is required");
        if (amount == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount is required");
        LocalDate date = request.debitNoteDate() == null ? LocalDate.now() : request.debitNoteDate();
        DebitNote dn = new DebitNote();
        dn.setOrganizationId(TenantContext.getOrganizationId());
        dn.setPurchaseReturnId(request.purchaseReturnId());
        dn.setPurchaseInvoiceId(request.purchaseInvoiceId());
        dn.setSupplierId(supplierId);
        dn.setDebitNoteDate(date);
        dn.setAmount(amount);
        dn.setNotes(request.notes());
        dn.setStatus("ISSUED");
        dn.setDebitNoteNumber(number(date));
        em.persist(dn);
        return dn;
    }

    public List<DebitNote> list() {
        return em.createQuery(
                        "from DebitNote d where d.organizationId=:org order by d.createdAt desc", DebitNote.class)
                .setParameter("org", TenantContext.getOrganizationId())
                .getResultList();
    }

    private String number(LocalDate d) {
        Organization o =
                organizations.findById(TenantContext.getOrganizationId()).orElseThrow();
        return numbers.next(o.getId(), "DEBIT_NOTE", "DN", "{PREFIX}/{FY}/{SEQ:6}", o.getFinancialYearStart(), d);
    }
}
