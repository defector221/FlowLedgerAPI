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
        Customer c = mapper.toEntity(dto);
        c.setCustomerCode(code);
        c.setOrganizationId(org);
        applyDefaults(c);
        Customer saved = repo.save(c);
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

    private void applyDefaults(Customer c) {
        if (c.getCountry() == null || c.getCountry().isBlank()) {
            c.setCountry("India");
        }
        if (c.getCreditLimit() == null) {
            c.setCreditLimit(BigDecimal.ZERO);
        }
        if (c.getOpeningBalance() == null) {
            c.setOpeningBalance(BigDecimal.ZERO);
        }
    }

    @Transactional(readOnly = true)
    public Response get(UUID id) {
        return mapper.toResponse(load(id));
    }

    public Response update(UUID id, Update dto) {
        Customer c = load(id);
        mapper.update(dto, c);
        Customer saved = repo.save(c);
        searchEvents.upsert(orgId(), SearchEntityType.CUSTOMER, saved.getId());
        return mapper.toResponse(saved);
    }

    public void archive(UUID id) {
        Customer c = load(id);
        c.setArchived(true);
        repo.save(c);
        searchEvents.delete(orgId(), SearchEntityType.CUSTOMER, id);
    }

    @Transactional(readOnly = true)
    public Page<Response> search(Search f, Pageable pageable) {
        UUID org = orgId();
        Specification<Customer> s = (r, q, b) -> b.equal(r.get("organizationId"), org);
        // Default: hide archived (soft-deleted) customers unless explicitly requested
        if (f.archived() == null) {
            s = s.and((r, q, b) -> b.equal(r.get("archived"), false));
        } else {
            s = s.and((r, q, b) -> b.equal(r.get("archived"), f.archived()));
        }
        if (f.search() != null && !f.search().isBlank()) {
            String p = "%" + f.search().toLowerCase() + "%";
            s = s.and((r, q, b) -> b.or(
                    b.like(b.lower(r.get("customerName")), p),
                    b.like(b.lower(r.get("customerCode")), p),
                    b.like(b.lower(r.get("phone")), p)));
        }
        return repo.findAll(s, pageable).map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Statement statement(UUID id) {
        Customer c = load(id);
        return new Statement(c.getOpeningBalance(), BigDecimal.ZERO, c.getOpeningBalance());
    }

    @Transactional(readOnly = true)
    public BigDecimal outstanding(UUID id) {
        return statement(id).balance();
    }

    private Customer load(UUID id) {
        return required(repo.findByIdAndOrganizationId(id, orgId()), "Customer");
    }
}
