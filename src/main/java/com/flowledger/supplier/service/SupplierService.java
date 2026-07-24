package com.flowledger.supplier.service;

import com.flowledger.common.service.OrganizationScopedService;
import com.flowledger.common.util.EntityCodeGenerator;
import com.flowledger.purchase.entity.PurchaseInvoice;
import com.flowledger.search.event.SearchIndexEventPublisher;
import com.flowledger.search.model.SearchEntityType;
import com.flowledger.supplier.dto.SupplierDtos.*;
import com.flowledger.supplier.entity.Supplier;
import com.flowledger.supplier.mapper.SupplierMapper;
import com.flowledger.supplier.repository.SupplierRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class SupplierService extends OrganizationScopedService {
    private final SupplierRepository repo;
    private final SupplierMapper mapper;
    private final SearchIndexEventPublisher searchEvents;

    @PersistenceContext
    private EntityManager em;

    public SupplierService(SupplierRepository repo, SupplierMapper mapper, SearchIndexEventPublisher searchEvents) {
        this.repo = repo;
        this.mapper = mapper;
        this.searchEvents = searchEvents;
    }

    public Response create(Create dto) {
        UUID org = orgId();
        String nameForCode =
                dto.companyName() != null && !dto.companyName().isBlank() ? dto.companyName() : dto.supplierName();
        String code = resolveCode(org, dto.supplierCode(), nameForCode);
        if (repo.existsByOrganizationIdAndSupplierCode(org, code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Supplier code already exists");
        }
        Supplier supplier = mapper.toEntity(dto);
        supplier.setSupplierCode(code);
        supplier.setOrganizationId(org);
        if (supplier.getCountry() == null || supplier.getCountry().isBlank()) {
            supplier.setCountry("India");
        }
        if (supplier.getOpeningBalance() == null) {
            supplier.setOpeningBalance(java.math.BigDecimal.ZERO);
        }
        Supplier saved = repo.save(supplier);
        searchEvents.upsert(org, SearchEntityType.SUPPLIER, saved.getId());
        return mapper.toResponse(saved);
    }

    private String resolveCode(UUID org, String provided, String name) {
        if (provided != null && !provided.isBlank()) {
            return provided.trim().toUpperCase(Locale.ROOT);
        }
        return EntityCodeGenerator.uniqueFromName(
                name, "SUP", candidate -> repo.existsByOrganizationIdAndSupplierCode(org, candidate));
    }

    @Transactional(readOnly = true)
    public Response get(UUID id) {
        return mapper.toResponse(load(id));
    }

    public Response update(UUID id, Update dto) {
        Supplier supplier = load(id);
        mapper.update(dto, supplier);
        if (dto.archived() != null) {
            supplier.setArchived(dto.archived());
        }
        Supplier saved = repo.save(supplier);
        if (saved.isArchived()) {
            searchEvents.delete(orgId(), SearchEntityType.SUPPLIER, saved.getId());
        } else {
            searchEvents.upsert(orgId(), SearchEntityType.SUPPLIER, saved.getId());
        }
        return mapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<Response> search(Search filter, Pageable pageable) {
        UUID org = orgId();
        Specification<Supplier> spec = (root, query, builder) -> builder.equal(root.get("organizationId"), org);
        if (filter.archived() == null) {
            spec = spec.and((root, query, builder) -> builder.equal(root.get("archived"), false));
        } else {
            spec = spec.and((root, query, builder) -> builder.equal(root.get("archived"), filter.archived()));
        }
        if (filter.search() != null && !filter.search().isBlank()) {
            String pattern = "%" + filter.search().toLowerCase() + "%";
            spec = spec.and((root, query, builder) -> builder.or(
                    builder.like(builder.lower(root.get("supplierName")), pattern),
                    builder.like(builder.lower(root.get("supplierCode")), pattern),
                    builder.like(builder.lower(root.get("phone")), pattern)));
        }
        return repo.findAll(spec, pageable).map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public AgingReport aging(UUID id, LocalDate asOfDate) {
        Supplier supplier = load(id);
        LocalDate asOf = asOfDate != null ? asOfDate : LocalDate.now();
        List<PurchaseInvoice> unpaid = em.createQuery(
                        """
                        from PurchaseInvoice i
                        where i.organizationId = :org and i.supplierId = :sid
                          and i.status not in ('DRAFT', 'CANCELLED')
                          and i.outstandingAmount > 0
                        order by i.invoiceDate asc
                        """,
                        PurchaseInvoice.class)
                .setParameter("org", orgId())
                .setParameter("sid", id)
                .getResultList();

        BigDecimal current = BigDecimal.ZERO;
        BigDecimal d30 = BigDecimal.ZERO;
        BigDecimal d60 = BigDecimal.ZERO;
        BigDecimal d90 = BigDecimal.ZERO;
        BigDecimal over90 = BigDecimal.ZERO;
        List<AgingLine> lines = new ArrayList<>();
        for (PurchaseInvoice invoice : unpaid) {
            LocalDate due = invoice.getDueDate() != null ? invoice.getDueDate() : invoice.getInvoiceDate();
            int daysOverdue = (int) Math.max(0, ChronoUnit.DAYS.between(due, asOf));
            String bucket = bucketName(daysOverdue);
            BigDecimal amount =
                    invoice.getOutstandingAmount() == null ? BigDecimal.ZERO : invoice.getOutstandingAmount();
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
                supplier.getId(),
                supplier.getSupplierName(),
                asOf,
                new AgingBuckets(current, d30, d60, d90, over90, total),
                lines);
    }

    static String bucketName(int daysOverdue) {
        if (daysOverdue <= 0) return "CURRENT";
        if (daysOverdue <= 30) return "1-30";
        if (daysOverdue <= 60) return "31-60";
        if (daysOverdue <= 90) return "61-90";
        return "90+";
    }

    private Supplier load(UUID id) {
        return required(repo.findByIdAndOrganizationId(id, orgId()), "Supplier");
    }
}
