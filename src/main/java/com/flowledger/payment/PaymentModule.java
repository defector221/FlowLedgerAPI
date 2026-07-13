package com.flowledger.payment;

import com.flowledger.common.entity.AuditedEntity;
import com.flowledger.common.tenant.TenantContext;
import com.flowledger.common.util.DocumentNumberService;
import com.flowledger.organization.entity.Organization;
import com.flowledger.organization.repository.OrganizationRepository;
import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.*;
import java.time.LocalDate;
import java.util.*;
import lombok.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
class Payment extends AuditedEntity {
    enum Type {
        RECEIPT,
        PAYMENT
    }

    enum Party {
        CUSTOMER,
        SUPPLIER
    }

    @Column(name = "payment_number")
    String paymentNumber;

    @Column(name = "payment_date")
    LocalDate paymentDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type")
    Type paymentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "party_type")
    Party partyType;

    UUID customerId, supplierId;
    BigDecimal amount;
    String paymentMode, transactionReference, bankReference;

    @Column(columnDefinition = "text")
    String notes;

    @Version
    Long version;

    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true)
    List<PaymentAllocation> allocations = new ArrayList<>();
}

@Entity
@Table(name = "payment_allocations")
@Getter
@Setter
@NoArgsConstructor
class PaymentAllocation {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    Payment payment;

    String documentType;
    UUID documentId;
    BigDecimal allocatedAmount;

    @Column(updatable = false)
    java.time.OffsetDateTime createdAt;

    @PrePersist
    void created() {
        createdAt = java.time.OffsetDateTime.now();
    }
}

record Allocation(
        @NotBlank String documentType, @NotNull UUID documentId, @NotNull @DecimalMin("0.01") BigDecimal amount) {}

record PaymentRequest(
        @NotNull LocalDate paymentDate,
        @NotNull Payment.Type paymentType,
        @NotNull Payment.Party partyType,
        UUID customerId,
        UUID supplierId,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @NotBlank String paymentMode,
        String transactionReference,
        String bankReference,
        String notes,
        List<@Valid Allocation> allocations) {}

@Service
@Transactional
class PaymentService {
    @PersistenceContext
    EntityManager em;

    private final DocumentNumberService numbers;
    private final OrganizationRepository organizations;

    PaymentService(DocumentNumberService n, OrganizationRepository o) {
        numbers = n;
        organizations = o;
    }

    Payment create(PaymentRequest r) {
        validate(r);
        BigDecimal allocated = r.allocations() == null
                ? BigDecimal.ZERO
                : r.allocations().stream().map(Allocation::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (allocated.compareTo(r.amount()) > 0) throw bad("Allocated amount exceeds payment amount");
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
        p.setPaymentNumber(number(r.paymentDate()));
        em.persist(p);
        if (r.allocations() != null) for (Allocation a : r.allocations()) allocate(p, a);
        return p;
    }

    Payment allocate(UUID id, Allocation a) {
        Payment p = get(id);
        allocate(p, a);
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

    Payment get(UUID id) {
        Payment p = em.find(Payment.class, id);
        if (p == null || !p.getOrganizationId().equals(TenantContext.getOrganizationId()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found");
        return p;
    }

    List<Payment> list() {
        return em.createQuery(
                        "from Payment p where p.organizationId=:org order by p.paymentDate desc,p.createdAt desc",
                        Payment.class)
                .setParameter("org", TenantContext.getOrganizationId())
                .getResultList();
    }

    private void validate(PaymentRequest r) {
        if (r.partyType() == Payment.Party.CUSTOMER && (r.customerId() == null || r.supplierId() != null))
            throw bad("Customer receipt requires only customerId");
        if (r.partyType() == Payment.Party.SUPPLIER && (r.supplierId() == null || r.customerId() != null))
            throw bad("Supplier payment requires only supplierId");
        if (r.paymentType() == Payment.Type.RECEIPT && r.partyType() != Payment.Party.CUSTOMER)
            throw bad("Receipt must be from a customer");
        if (r.paymentType() == Payment.Type.PAYMENT && r.partyType() != Payment.Party.SUPPLIER)
            throw bad("Payment must be to a supplier");
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

@RestController
@RequestMapping("/api/v1/payments")
class PaymentController {
    private final PaymentService service;

    PaymentController(PaymentService s) {
        service = s;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Payment create(@Valid @RequestBody PaymentRequest r) {
        return service.create(r);
    }

    @PostMapping("/{id}/allocations")
    Payment allocate(@PathVariable UUID id, @Valid @RequestBody Allocation a) {
        return service.allocate(id, a);
    }

    @GetMapping("/{id}")
    Payment get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping
    List<Payment> list() {
        return service.list();
    }
}
