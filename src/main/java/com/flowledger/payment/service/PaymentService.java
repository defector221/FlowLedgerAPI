package com.flowledger.payment.service;

import com.flowledger.accounting.domain.AccountingStatus;
import com.flowledger.accounting.domain.JournalSource;
import com.flowledger.accounting.service.AccountingPostingService;
import com.flowledger.common.tenant.TenantContext;
import com.flowledger.common.util.DocumentNumberService;
import com.flowledger.organization.entity.Organization;
import com.flowledger.organization.repository.OrganizationRepository;
import com.flowledger.payment.dto.PaymentDtos.Allocation;
import com.flowledger.payment.dto.PaymentDtos.PaymentRequest;
import com.flowledger.payment.entity.Payment;
import com.flowledger.payment.entity.PaymentAllocation;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class PaymentService {
    @PersistenceContext
    private EntityManager em;

    private final DocumentNumberService numbers;
    private final OrganizationRepository organizations;
    private final AccountingPostingService accounting;

    public PaymentService(
            DocumentNumberService numbers, OrganizationRepository organizations, AccountingPostingService accounting) {
        this.numbers = numbers;
        this.organizations = organizations;
        this.accounting = accounting;
    }

    public Payment create(PaymentRequest r) {
        validate(r);
        BigDecimal allocated = r.allocations() == null
                ? BigDecimal.ZERO
                : r.allocations().stream().map(Allocation::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (allocated.compareTo(r.amount()) > 0) {
            throw bad("Allocated amount exceeds payment amount");
        }
        Payment p = new Payment();
        p.setOrganizationId(TenantContext.getOrganizationId());
        p.setPaymentDate(r.paymentDate());
        p.setPaymentType(r.paymentType());
        p.setPartyType(r.partyType());
        p.setCustomerId(r.customerId());
        p.setSupplierId(r.supplierId());
        p.setAmount(r.amount());
        p.setPaymentMode(r.paymentMode());
        p.setTransactionReference(r.transactionReference());
        p.setBankReference(r.bankReference());
        p.setNotes(r.notes());
        p.setStatus("ACTIVE");
        p.setPaymentNumber(number(r.paymentDate()));
        em.persist(p);
        if (r.allocations() != null) {
            for (Allocation a : r.allocations()) {
                allocate(p, a);
            }
        }
        accounting.postPayment(p);
        return p;
    }

    public Payment allocate(UUID id, Allocation a) {
        return allocate(id, List.of(a));
    }

    public Payment allocate(UUID id, List<Allocation> allocations) {
        if (allocations == null || allocations.isEmpty()) {
            throw bad("At least one allocation is required");
        }
        Payment p = get(id);
        if ("CANCELLED".equals(p.getStatus())) {
            throw bad("Cannot allocate a cancelled payment");
        }
        for (Allocation a : allocations) {
            allocate(p, a);
        }
        if (p.getAccountingStatus() != AccountingStatus.POSTED) {
            accounting.postPayment(p);
        }
        return p;
    }

    private void allocate(Payment p, Allocation a) {
        String type = a.documentType().toUpperCase(Locale.ROOT);
        if (!Set.of("SALES_INVOICE", "PURCHASE_INVOICE").contains(type)) {
            throw bad("Only sales and purchase invoices can be allocated");
        }
        String table = type.equals("SALES_INVOICE") ? "sales_invoices" : "purchase_invoices";
        @SuppressWarnings("unchecked")
        List<Number> outstandingRows = em.createNativeQuery(
                        "select outstanding_amount from " + table + " where id=:id and organization_id=:org")
                .setParameter("id", a.documentId())
                .setParameter("org", TenantContext.getOrganizationId())
                .getResultList();
        if (outstandingRows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found");
        }
        BigDecimal outstanding = new BigDecimal(outstandingRows.get(0).toString());
        if (outstanding.compareTo(a.amount()) < 0) {
            throw bad("Allocation exceeds invoice outstanding amount");
        }
        BigDecimal existing = p.getAllocations().stream()
                .map(PaymentAllocation::getAllocatedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (existing.add(a.amount()).compareTo(p.getAmount()) > 0) {
            throw bad("Allocation exceeds payment amount");
        }
        PaymentAllocation pa = new PaymentAllocation();
        pa.setPayment(p);
        pa.setDocumentType(type);
        pa.setDocumentId(a.documentId());
        pa.setAllocatedAmount(a.amount());
        p.getAllocations().add(pa);
        em.createNativeQuery("update " + table + " set amount_paid = amount_paid + :amount, "
                        + "outstanding_amount = outstanding_amount - :amount, "
                        + "payment_status = case when outstanding_amount - :amount <= 0 then 'PAID' else 'PARTIALLY_PAID' end, "
                        + "status = case when outstanding_amount - :amount <= 0 then 'PAID' else 'PARTIALLY_PAID' end "
                        + "where id = :id and organization_id = :org")
                .setParameter("amount", a.amount())
                .setParameter("id", a.documentId())
                .setParameter("org", TenantContext.getOrganizationId())
                .executeUpdate();
    }

    public Payment get(UUID id) {
        Payment p = em.find(Payment.class, id);
        if (p == null || !p.getOrganizationId().equals(TenantContext.getOrganizationId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found");
        }
        p.getAllocations().size(); // initialize
        return p;
    }

    public List<Payment> list() {
        return list(null, null, null, null, null, null, null, null);
    }

    public List<Payment> list(
            Payment.Type type,
            Payment.Party partyType,
            String status,
            UUID customerId,
            UUID supplierId,
            LocalDate from,
            LocalDate to,
            String search) {
        StringBuilder jpql = new StringBuilder("from Payment p where p.organizationId=:org");
        List<String> conditions = new ArrayList<>();
        if (type != null) {
            conditions.add("p.paymentType = :type");
        }
        if (partyType != null) {
            conditions.add("p.partyType = :partyType");
        }
        if (status != null && !status.isBlank()) {
            conditions.add("p.status = :status");
        }
        if (customerId != null) {
            conditions.add("p.customerId = :customerId");
        }
        if (supplierId != null) {
            conditions.add("p.supplierId = :supplierId");
        }
        if (from != null) {
            conditions.add("p.paymentDate >= :from");
        }
        if (to != null) {
            conditions.add("p.paymentDate <= :to");
        }
        if (search != null && !search.isBlank()) {
            conditions.add(
                    "(lower(p.paymentNumber) like :search or lower(coalesce(p.transactionReference,'')) like :search or lower(coalesce(p.notes,'')) like :search)");
        }
        for (String condition : conditions) {
            jpql.append(" and ").append(condition);
        }
        jpql.append(" order by p.paymentDate desc, p.createdAt desc");

        TypedQuery<Payment> query =
                em.createQuery(jpql.toString(), Payment.class).setParameter("org", TenantContext.getOrganizationId());
        if (type != null) {
            query.setParameter("type", type);
        }
        if (partyType != null) {
            query.setParameter("partyType", partyType);
        }
        if (status != null && !status.isBlank()) {
            query.setParameter("status", status.trim().toUpperCase(Locale.ROOT));
        }
        if (customerId != null) {
            query.setParameter("customerId", customerId);
        }
        if (supplierId != null) {
            query.setParameter("supplierId", supplierId);
        }
        if (from != null) {
            query.setParameter("from", from);
        }
        if (to != null) {
            query.setParameter("to", to);
        }
        if (search != null && !search.isBlank()) {
            query.setParameter("search", "%" + search.trim().toLowerCase(Locale.ROOT) + "%");
        }
        List<Payment> rows = query.getResultList();
        rows.forEach(p -> p.getAllocations().size());
        return rows;
    }

    public Payment cancel(UUID id) {
        Payment p = get(id);
        if ("CANCELLED".equals(p.getStatus())) {
            throw bad("Payment is already cancelled");
        }
        for (PaymentAllocation a : List.copyOf(p.getAllocations())) {
            reverseAllocation(a);
        }
        p.getAllocations().clear();
        p.setStatus("CANCELLED");
        if (p.getAccountingStatus() == AccountingStatus.POSTED) {
            JournalSource source = p.getPaymentType() == Payment.Type.RECEIPT
                    ? JournalSource.CUSTOMER_RECEIPT
                    : JournalSource.SUPPLIER_PAYMENT;
            accounting.reverseDocumentJournal(p.getOrganizationId(), source, p.getId());
            p.setAccountingStatus(AccountingStatus.REVERSED);
        }
        return p;
    }

    private void reverseAllocation(PaymentAllocation a) {
        String type = a.getDocumentType().toUpperCase(Locale.ROOT);
        String table = type.equals("SALES_INVOICE") ? "sales_invoices" : "purchase_invoices";
        em.createNativeQuery("update " + table + " set amount_paid = GREATEST(amount_paid - :amount, 0), "
                        + "outstanding_amount = outstanding_amount + :amount, "
                        + "payment_status = case when amount_paid - :amount <= 0 then 'UNPAID' "
                        + "when outstanding_amount + :amount > 0 then 'PARTIALLY_PAID' else payment_status end, "
                        + "status = case when amount_paid - :amount <= 0 then 'CONFIRMED' "
                        + "when outstanding_amount + :amount > 0 then 'PARTIALLY_PAID' else status end "
                        + "where id = :id and organization_id = :org")
                .setParameter("amount", a.getAllocatedAmount())
                .setParameter("id", a.getDocumentId())
                .setParameter("org", TenantContext.getOrganizationId())
                .executeUpdate();
    }

    private void validate(PaymentRequest r) {
        if (r.partyType() == Payment.Party.CUSTOMER && (r.customerId() == null || r.supplierId() != null)) {
            throw bad("Customer receipt requires only customerId");
        }
        if (r.partyType() == Payment.Party.SUPPLIER && (r.supplierId() == null || r.customerId() != null)) {
            throw bad("Supplier payment requires only supplierId");
        }
        if (r.paymentType() == Payment.Type.RECEIPT && r.partyType() != Payment.Party.CUSTOMER) {
            throw bad("Receipt must be from a customer");
        }
        if (r.paymentType() == Payment.Type.PAYMENT && r.partyType() != Payment.Party.SUPPLIER) {
            throw bad("Payment must be to a supplier");
        }
    }

    private String number(LocalDate d) {
        Organization o =
                organizations.findById(TenantContext.getOrganizationId()).orElseThrow();
        return numbers.next(
                o.getId(), "PAYMENT", o.getPaymentPrefix(), "{PREFIX}/{FY}/{SEQ:6}", o.getFinancialYearStart(), d);
    }

    private ResponseStatusException bad(String s) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, s);
    }
}
