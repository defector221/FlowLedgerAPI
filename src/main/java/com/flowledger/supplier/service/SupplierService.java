package com.flowledger.supplier.service;

import com.flowledger.common.service.OrganizationScopedService;
import com.flowledger.search.event.SearchIndexEventPublisher;
import com.flowledger.search.model.SearchEntityType;
import com.flowledger.supplier.dto.SupplierDtos.*;
import com.flowledger.supplier.entity.Supplier;
import com.flowledger.supplier.mapper.SupplierMapper;
import com.flowledger.supplier.repository.SupplierRepository;
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

    public SupplierService(SupplierRepository repo, SupplierMapper mapper, SearchIndexEventPublisher searchEvents) {
        this.repo = repo;
        this.mapper = mapper;
        this.searchEvents = searchEvents;
    }

    public Response create(Create dto) {
        UUID org = orgId();
        if (repo.existsByOrganizationIdAndSupplierCode(org, dto.supplierCode())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Supplier code already exists");
        }
        Supplier supplier = mapper.toEntity(dto);
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

    private Supplier load(UUID id) {
        return required(repo.findByIdAndOrganizationId(id, orgId()), "Supplier");
    }
}
