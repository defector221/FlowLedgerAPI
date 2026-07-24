package com.flowledger.customer.service;

import com.flowledger.common.service.OrganizationScopedService;
import com.flowledger.common.util.EntityCodeGenerator;
import com.flowledger.customer.dto.CustomerDtos.*;
import com.flowledger.customer.entity.Customer;
import com.flowledger.customer.mapper.CustomerMapper;
import com.flowledger.customer.repository.CustomerRepository;
import com.flowledger.payment.entity.Payment;
import com.flowledger.sales.entity.SalesInvoice;
import com.flowledger.search.event.SearchIndexEventPublisher;
import com.flowledger.search.model.SearchEntityType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class CustomerService extends OrganizationScopedService {
    private final CustomerRepository repo;
    private final CustomerMapper mapper;
    private final SearchIndexEventPublisher searchEvents;

    @PersistenceContext
    private EntityManager em;

    public CustomerService(CustomerRepository repo, CustomerMapper mapper, SearchIndexEventPublisher searchEvents) {
        this.repo = repo;
        this.mapper = mapper;
        this.searchEvents = searchEvents;
    }

    public Response create(Create dto) {
        UUID org = orgId();
        String code = resolveCode(org, dto.customerCode(), dto.customerName());
        if (repo.existsByOrganizationIdAndCustomerCode(org, code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Customer code already exists");
        }
        Customer customer = mapper.toEntity(dto);
        customer.setCustomerCode(code);
        customer.setOrganizationId(org);
        applyDefaults(customer);
        Customer saved = repo.save(customer);
        searchEvents.upsert(org, SearchEntityType.CUSTOMER, saved.getId());
        return mapper.toResponse(saved);
    }

    private String resolveCode(UUID org, String provided, String name) {
        if (provided != null && !provided.isBlank()) {
            return provided.trim().toUpperCase(Locale.ROOT);
        }
        return EntityCodeGenerator.uniqueFromName(
                name, "CUST", candidate -> repo.existsByOrganizationIdAndCustomerCode(org, candidate));
    }

    private void applyDefaults(Customer customer) {
        if (customer.getCountry() == null || customer.getCountry().isBlank()) {
            customer.setCountry("India");
        }
        if (customer.getCreditLimit() == null) {
            customer.setCreditLimit(BigDecimal.ZERO);
        }
        if (customer.getOpeningBalance() == null) {
            customer.setOpeningBalance(BigDecimal.ZERO);
        }
    }

    @Transactional(readOnly = true)
    public Response get(UUID id) {
        return mapper.toResponse(load(id));
    }

    public Response update(UUID id, Update dto) {
        Customer customer = load(id);
        mapper.update(dto, customer);
        Customer saved = repo.save(customer);
        searchEvents.upsert(orgId(), SearchEntityType.CUSTOMER, saved.getId());
        return mapper.toResponse(saved);
    }

    public void archive(UUID id) {
        Customer customer = load(id);
        customer.setArchived(true);
        repo.save(customer);
        searchEvents.delete(orgId(), SearchEntityType.CUSTOMER, id);
    }

    @Transactional(readOnly = true)
    public Page<Response> search(Search filter, Pageable pageable) {
        UUID org = orgId();
        Specification<Customer> spec = (root, query, cb) -> cb.equal(root.get("organizationId"), org);
        // Default: hide archived (soft-deleted) customers unless explicitly requested
        if (filter.archived() == null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("archived"), false));
        } else {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("archived"), filter.archived()));
        }
        if (filter.search() != null && !filter.search().isBlank()) {
            String raw = filter.search().trim();
            String likePattern = "%" + raw.toLowerCase() + "%";
            String digits = raw.replaceAll("\\D", "");
            spec = spec.and((root, query, cb) -> {
                var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
                predicates.add(cb.like(cb.lower(root.get("customerName")), likePattern));
                predicates.add(cb.like(cb.lower(root.get("customerCode")), likePattern));
                predicates.add(cb.like(cb.lower(root.get("phone")), likePattern));
                predicates.add(cb.like(cb.lower(cb.coalesce(root.get("companyName"), "")), likePattern));
                predicates.add(cb.like(cb.lower(cb.coalesce(root.get("email"), "")), likePattern));
                // Mobile search: match digits even when phone is stored with spaces / +91
                if (digits.length() >= 3) {
                    String digitLike = "%" + digits + "%";
                    predicates.add(cb.like(cb.coalesce(root.get("phone"), ""), digitLike));
                    predicates.add(cb.like(
                            cb.function(
                                    "regexp_replace",
                                    String.class,
                                    cb.coalesce(root.get("phone"), ""),
                                    cb.literal("[^0-9]"),
                                    cb.literal(""),
                                    cb.literal("g")),
                            digitLike));
                }
                return cb.or(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
            });
        }
        return repo.findAll(spec, pageable).map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Statement statement(UUID id) {
        Customer customer = load(id);
        UUID org = orgId();
        BigDecimal opening = nz(customer.getOpeningBalance());

        List<SalesInvoice> invoices = em.createQuery(
                        """
                        from SalesInvoice i
                        where i.organizationId = :org and i.customerId = :cid
                          and i.status not in :excluded
                        order by i.invoiceDate asc, i.createdAt asc
                        """,
                        SalesInvoice.class)
                .setParameter("org", org)
                .setParameter("cid", id)
                .setParameter("excluded", List.of(SalesInvoice.Status.DRAFT, SalesInvoice.Status.CANCELLED))
                .getResultList();

        List<Payment> receipts = em.createQuery(
                        """
                        from Payment p
                        where p.organizationId = :org and p.customerId = :cid
                          and p.paymentType = :ptype
                          and p.status <> 'CANCELLED'
                        order by p.paymentDate asc, p.createdAt asc
                        """,
                        Payment.class)
                .setParameter("org", org)
                .setParameter("cid", id)
                .setParameter("ptype", Payment.Type.RECEIPT)
                .getResultList();

        record Timed(LocalDate date, String type, String number, UUID docId, BigDecimal debit, BigDecimal credit) {}
        List<Timed> events = new ArrayList<>();
        BigDecimal invoicesTotal = BigDecimal.ZERO;
        BigDecimal outstanding = BigDecimal.ZERO;
        for (SalesInvoice invoice : invoices) {
            BigDecimal amount = nz(invoice.getGrandTotal());
            invoicesTotal = invoicesTotal.add(amount);
            outstanding = outstanding.add(nz(invoice.getOutstandingAmount()));
            events.add(new Timed(
                    invoice.getInvoiceDate(),
                    "SALES_INVOICE",
                    invoice.getInvoiceNumber(),
                    invoice.getId(),
                    amount,
                    BigDecimal.ZERO));
        }
        BigDecimal receiptsTotal = BigDecimal.ZERO;
        for (Payment receipt : receipts) {
            BigDecimal amount = nz(receipt.getAmount());
            receiptsTotal = receiptsTotal.add(amount);
            events.add(new Timed(
                    receipt.getPaymentDate(),
                    "RECEIPT",
                    receipt.getPaymentNumber(),
                    receipt.getId(),
                    BigDecimal.ZERO,
                    amount));
        }
        events.sort(Comparator.comparing(Timed::date, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Timed::type));

        List<StatementEntry> entries = new ArrayList<>();
        BigDecimal running = opening;
        for (Timed event : events) {
            running = running.add(event.debit()).subtract(event.credit());
            entries.add(new StatementEntry(
                    event.date(), event.type(), event.number(), event.docId(), event.debit(), event.credit(), running));
        }
        return new Statement(opening, outstanding, running, invoicesTotal, receiptsTotal, entries);
    }

    @Transactional(readOnly = true)
    public AgingReport aging(UUID id, LocalDate asOfDate) {
        Customer customer = load(id);
        LocalDate asOf = asOfDate != null ? asOfDate : LocalDate.now();
        List<SalesInvoice> unpaid = em.createQuery(
                        """
                        from SalesInvoice i
                        where i.organizationId = :org and i.customerId = :cid
                          and i.status not in :excluded
                          and i.outstandingAmount > 0
                        order by i.invoiceDate asc
                        """,
                        SalesInvoice.class)
                .setParameter("org", orgId())
                .setParameter("cid", id)
                .setParameter("excluded", List.of(SalesInvoice.Status.DRAFT, SalesInvoice.Status.CANCELLED))
                .getResultList();

        BigDecimal current = BigDecimal.ZERO;
        BigDecimal d30 = BigDecimal.ZERO;
        BigDecimal d60 = BigDecimal.ZERO;
        BigDecimal d90 = BigDecimal.ZERO;
        BigDecimal over90 = BigDecimal.ZERO;
        List<AgingLine> lines = new ArrayList<>();
        for (SalesInvoice invoice : unpaid) {
            LocalDate due = invoice.getDueDate() != null ? invoice.getDueDate() : invoice.getInvoiceDate();
            int daysOverdue = (int) Math.max(0, ChronoUnit.DAYS.between(due, asOf));
            String bucket = bucketName(daysOverdue);
            BigDecimal amount = nz(invoice.getOutstandingAmount());
            switch (bucket) {
                case "CURRENT" -> current = current.add(amount);
                case "1-30" -> d30 = d30.add(amount);
                case "31-60" -> d60 = d60.add(amount);
                case "61-90" -> d90 = d90.add(amount);
                default -> over90 = over90.add(amount);
            }
            lines.add(new AgingLine(
                    invoice.getId(),
                    invoice.getInvoiceNumber(),
                    invoice.getInvoiceDate(),
                    due,
                    amount,
                    daysOverdue,
                    bucket));
        }
        BigDecimal total = current.add(d30).add(d60).add(d90).add(over90);
        return new AgingReport(
                customer.getId(),
                customer.getCustomerName(),
                asOf,
                new AgingBuckets(current, d30, d60, d90, over90, total),
                lines);
    }

    @Transactional(readOnly = true)
    public BigDecimal outstanding(UUID id) {
        return statement(id).invoicesOutstanding();
    }

    static String bucketName(int daysOverdue) {
        if (daysOverdue <= 0) return "CURRENT";
        if (daysOverdue <= 30) return "1-30";
        if (daysOverdue <= 60) return "31-60";
        if (daysOverdue <= 90) return "61-90";
        return "90+";
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private Customer load(UUID id) {
        return required(repo.findByIdAndOrganizationId(id, orgId()), "Customer");
    }
}
