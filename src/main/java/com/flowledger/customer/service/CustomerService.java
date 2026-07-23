package com.flowledger.customer.service;

import com.flowledger.common.service.OrganizationScopedService;
import com.flowledger.common.util.EntityCodeGenerator;
import com.flowledger.customer.dto.CustomerDtos.*;
import com.flowledger.customer.entity.Customer;
import com.flowledger.customer.mapper.CustomerMapper;
import com.flowledger.customer.repository.CustomerRepository;
import com.flowledger.search.event.SearchIndexEventPublisher;
import com.flowledger.search.model.SearchEntityType;
import java.math.*;
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
        return new Statement(customer.getOpeningBalance(), BigDecimal.ZERO, customer.getOpeningBalance());
    }

    @Transactional(readOnly = true)
    public BigDecimal outstanding(UUID id) {
        return statement(id).balance();
    }

    private Customer load(UUID id) {
        return required(repo.findByIdAndOrganizationId(id, orgId()), "Customer");
    }
}
