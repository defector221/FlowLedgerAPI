package com.flowledger.payment.service;

import com.flowledger.accounting.domain.AccountSubType;
import com.flowledger.accounting.domain.AccountingStatus;
import com.flowledger.accounting.domain.JournalSource;
import com.flowledger.accounting.entity.Account;
import com.flowledger.accounting.repository.AccountRepository;
import com.flowledger.common.tenant.TenantContext;
import com.flowledger.common.util.DocumentNumberService;
import com.flowledger.common.util.FinancialYearUtil;
import com.flowledger.finance.voucher.adapter.ContraVoucherBuilder;
import com.flowledger.finance.voucher.adapter.DocumentVoucherFacade;
import com.flowledger.finance.voucher.adapter.PaymentVoucherBuilder;
import com.flowledger.organization.entity.Organization;
import com.flowledger.organization.repository.OrganizationRepository;
import com.flowledger.payment.dto.PaymentDtos.Allocation;
import com.flowledger.payment.dto.PaymentDtos.ContraRequest;
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
    private final DocumentVoucherFacade documentPosting;
    private final AccountRepository accounts;

    public PaymentService(
            DocumentNumberService numbers,
            OrganizationRepository organizations,
            DocumentVoucherFacade documentPosting,
            AccountRepository accounts) {
        this.numbers = numbers;
        this.organizations = organizations;
        this.documentPosting = documentPosting;
        this.accounts = accounts;
    }

    public Payment create(PaymentRequest request) {
        if (request.paymentType() == Payment.Type.CONTRA) {
            throw bad("Use POST /api/v1/payments/contra for CONTRA transfers");
        }
        validate(request);
        BigDecimal allocated = request.allocations() == null
                ? BigDecimal.ZERO
                : request.allocations().stream().map(Allocation::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (allocated.compareTo(request.amount()) > 0) {
            throw bad("Allocated amount exceeds payment amount");
        }
        Payment payment = new Payment();
        payment.setOrganizationId(TenantContext.getOrganizationId());
        payment.setPaymentDate(request.paymentDate());
        payment.setPaymentType(request.paymentType());
        payment.setPartyType(request.partyType());
        payment.setCustomerId(request.customerId());
        payment.setSupplierId(request.supplierId());
        payment.setAmount(request.amount());
        payment.setPaymentMode(request.paymentMode());
        payment.setTransactionReference(request.transactionReference());
        payment.setBankReference(request.bankReference());
        payment.setNotes(request.notes());
        payment.setStatus("ACTIVE");
        payment.setPaymentNumber(number(request.paymentDate()));
        em.persist(payment);
        if (request.allocations() != null) {
            for (Allocation allocation : request.allocations()) {
                allocate(payment, allocation);
            }
        }
        documentPosting.postPayment(payment);
        return payment;
    }

    public Payment createContra(ContraRequest request) {
        UUID org = TenantContext.getOrganizationId();
        if (request.fromAccountId().equals(request.toAccountId())) {
            throw bad("fromAccountId and toAccountId must differ");
        }
        Account from = requireCashOrBank(org, request.fromAccountId(), "fromAccountId");
        Account to = requireCashOrBank(org, request.toAccountId(), "toAccountId");
        Payment payment = new Payment();
        payment.setOrganizationId(org);
        payment.setPaymentDate(request.date());
        payment.setPaymentType(Payment.Type.CONTRA);
        payment.setPartyType(Payment.Party.INTERNAL);
        payment.setFromAccountId(from.getId());
        payment.setToAccountId(to.getId());
        payment.setAmount(request.amount());
        payment.setPaymentMode("BANK_TRANSFER");
        payment.setTransactionReference(request.transactionReference());
        payment.setNotes(request.notes());
        payment.setStatus("ACTIVE");
        payment.setPaymentNumber(number(request.date()));
        em.persist(payment);
        documentPosting.postContra(payment);
        return payment;
    }

    private Account requireCashOrBank(UUID org, UUID accountId, String field) {
        Account account = accounts.findByIdAndOrganizationId(accountId, org)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, field + " not found"));
        AccountSubType sub = account.getAccountSubType();
        boolean cashOrBank = sub == AccountSubType.CASH
                || sub == AccountSubType.BANK
                || account.getSystemAccountKey() == com.flowledger.accounting.domain.SystemAccountKey.CASH
                || account.getSystemAccountKey() == com.flowledger.accounting.domain.SystemAccountKey.BANK;
        if (!cashOrBank) {
            throw bad(field + " must be a cash or bank account");
        }
        if (!account.isActive()) {
            throw bad(field + " is inactive");
        }
        return account;
    }

    public Payment allocate(UUID id, Allocation allocation) {
        return allocate(id, List.of(allocation));
    }

    public Payment allocate(UUID id, List<Allocation> allocations) {
        if (allocations == null || allocations.isEmpty()) {
            throw bad("At least one allocation is required");
        }
        Payment payment = get(id);
        if ("CANCELLED".equals(payment.getStatus())) {
            throw bad("Cannot allocate a cancelled payment");
        }
        for (Allocation allocation : allocations) {
            allocate(payment, allocation);
        }
        if (payment.getAccountingStatus() != AccountingStatus.POSTED) {
            documentPosting.postPayment(payment);
        }
        return payment;
    }

    private void allocate(Payment payment, Allocation allocation) {
        String type = allocation.documentType().toUpperCase(Locale.ROOT);
        if (!Set.of("SALES_INVOICE", "PURCHASE_INVOICE").contains(type)) {
            throw bad("Only sales and purchase invoices can be allocated");
        }
        String table = type.equals("SALES_INVOICE") ? "sales_invoices" : "purchase_invoices";
        @SuppressWarnings("unchecked")
        List<Number> outstandingRows = em.createNativeQuery(
                        "select outstanding_amount from " + table + " where id=:id and organization_id=:org")
                .setParameter("id", allocation.documentId())
                .setParameter("org", TenantContext.getOrganizationId())
                .getResultList();
        if (outstandingRows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found");
        }
        BigDecimal outstanding = new BigDecimal(outstandingRows.get(0).toString());
        if (outstanding.compareTo(allocation.amount()) < 0) {
            throw bad("Allocation exceeds invoice outstanding amount");
        }
        BigDecimal existing = payment.getAllocations().stream()
                .map(PaymentAllocation::getAllocatedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (existing.add(allocation.amount()).compareTo(payment.getAmount()) > 0) {
            throw bad("Allocation exceeds payment amount");
        }
        PaymentAllocation pa = new PaymentAllocation();
        pa.setPayment(payment);
        pa.setDocumentType(type);
        pa.setDocumentId(allocation.documentId());
        pa.setAllocatedAmount(allocation.amount());
        payment.getAllocations().add(pa);
        em.createNativeQuery("update " + table + " set amount_paid = amount_paid + :amount, "
                        + "outstanding_amount = outstanding_amount - :amount, "
                        + "payment_status = case when outstanding_amount - :amount <= 0 then 'PAID' else 'PARTIALLY_PAID' end, "
                        + "status = case when outstanding_amount - :amount <= 0 then 'PAID' else 'PARTIALLY_PAID' end "
                        + "where id = :id and organization_id = :org")
                .setParameter("amount", allocation.amount())
                .setParameter("id", allocation.documentId())
                .setParameter("org", TenantContext.getOrganizationId())
                .executeUpdate();
    }

    public Payment get(UUID id) {
        Payment payment = em.find(Payment.class, id);
        if (payment == null || !payment.getOrganizationId().equals(TenantContext.getOrganizationId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found");
        }
        payment.getAllocations().size(); // initialize
        return payment;
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
        rows.forEach(payment -> payment.getAllocations().size());
        return rows;
    }

    public Payment cancel(UUID id) {
        Payment payment = get(id);
        if ("CANCELLED".equals(payment.getStatus())) {
            throw bad("Payment is already cancelled");
        }
        for (PaymentAllocation allocation : List.copyOf(payment.getAllocations())) {
            reverseAllocation(allocation);
        }
        payment.getAllocations().clear();
        payment.setStatus("CANCELLED");
        if (payment.getAccountingStatus() == AccountingStatus.POSTED) {
            JournalSource source;
            String referenceType;
            if (payment.getPaymentType() == Payment.Type.CONTRA) {
                source = JournalSource.CONTRA;
                referenceType = ContraVoucherBuilder.REFERENCE_TYPE;
            } else if (payment.getPaymentType() == Payment.Type.RECEIPT) {
                source = JournalSource.CUSTOMER_RECEIPT;
                referenceType = PaymentVoucherBuilder.REFERENCE_TYPE_RECEIPT;
            } else {
                source = JournalSource.SUPPLIER_PAYMENT;
                referenceType = PaymentVoucherBuilder.REFERENCE_TYPE_PAYMENT;
            }
            documentPosting.reverseDocument(payment.getOrganizationId(), referenceType, payment.getId(), source);
            payment.setAccountingStatus(AccountingStatus.REVERSED);
        }
        return payment;
    }

    private void reverseAllocation(PaymentAllocation allocation) {
        String type = allocation.getDocumentType().toUpperCase(Locale.ROOT);
        String table = type.equals("SALES_INVOICE") ? "sales_invoices" : "purchase_invoices";
        em.createNativeQuery("update " + table + " set amount_paid = GREATEST(amount_paid - :amount, 0), "
                        + "outstanding_amount = outstanding_amount + :amount, "
                        + "payment_status = case when amount_paid - :amount <= 0 then 'UNPAID' "
                        + "when outstanding_amount + :amount > 0 then 'PARTIALLY_PAID' else payment_status end, "
                        + "status = case when amount_paid - :amount <= 0 then 'CONFIRMED' "
                        + "when outstanding_amount + :amount > 0 then 'PARTIALLY_PAID' else status end "
                        + "where id = :id and organization_id = :org")
                .setParameter("amount", allocation.getAllocatedAmount())
                .setParameter("id", allocation.getDocumentId())
                .setParameter("org", TenantContext.getOrganizationId())
                .executeUpdate();
    }

    private void validate(PaymentRequest request) {
        if (request.paymentType() == Payment.Type.CONTRA || request.partyType() == Payment.Party.INTERNAL) {
            throw bad("Use POST /api/v1/payments/contra for CONTRA transfers");
        }
        if (request.partyType() == Payment.Party.CUSTOMER
                && (request.customerId() == null || request.supplierId() != null)) {
            throw bad("Customer receipt requires only customerId");
        }
        if (request.partyType() == Payment.Party.SUPPLIER
                && (request.supplierId() == null || request.customerId() != null)) {
            throw bad("Supplier payment requires only supplierId");
        }
        if (request.paymentType() == Payment.Type.RECEIPT && request.partyType() != Payment.Party.CUSTOMER) {
            throw bad("Receipt must be from a customer");
        }
        if (request.paymentType() == Payment.Type.PAYMENT && request.partyType() != Payment.Party.SUPPLIER) {
            throw bad("Payment must be to a supplier");
        }
    }

    private String number(LocalDate date) {
        Organization organization =
                organizations.findById(TenantContext.getOrganizationId()).orElseThrow();
        UUID org = organization.getId();
        String fy = FinancialYearUtil.financialYear(date, organization.getFinancialYearStart());
        String prefix = organization.getPaymentPrefix() == null
                        || organization.getPaymentPrefix().isBlank()
                ? "PAY"
                : organization.getPaymentPrefix();
        long maxExisting = maxPaymentSequence(org, prefix + "/" + fy + "/");
        numbers.ensureNextAtLeast(org, "PAYMENT", prefix, organization.getFinancialYearStart(), date, maxExisting + 1);
        for (int attempt = 0; attempt < 8; attempt++) {
            String candidate = numbers.next(
                    org, "PAYMENT", prefix, "{PREFIX}/{FY}/{SEQ:6}", organization.getFinancialYearStart(), date);
            if (!paymentNumberExists(org, candidate)) {
                return candidate;
            }
        }
        throw new ResponseStatusException(
                HttpStatus.CONFLICT, "Unable to allocate a unique payment number. Please retry.");
    }

    private long maxPaymentSequence(UUID org, String numberPrefix) {
        Object result = em.createNativeQuery(
                        """
                        SELECT COALESCE(MAX(NULLIF(regexp_replace(payment_number, '.*/', ''), '')::bigint), 0)
                        FROM payments
                        WHERE organization_id = :org
                          AND payment_number LIKE :pattern
                        """)
                .setParameter("org", org)
                .setParameter("pattern", numberPrefix + "%")
                .getSingleResult();
        if (result == null) return 0;
        return ((Number) result).longValue();
    }

    private boolean paymentNumberExists(UUID org, String paymentNumber) {
        Long count = em.createQuery(
                        "select count(p) from Payment p where p.organizationId = :org and p.paymentNumber = :number",
                        Long.class)
                .setParameter("org", org)
                .setParameter("number", paymentNumber)
                .getSingleResult();
        return count != null && count > 0;
    }

    private ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
