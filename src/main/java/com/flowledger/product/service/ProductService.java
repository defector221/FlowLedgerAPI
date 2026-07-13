package com.flowledger.product.service;

import com.flowledger.common.service.OrganizationScopedService;
import com.flowledger.product.dto.ProductDtos.*;
import com.flowledger.product.entity.Product;
import com.flowledger.product.mapper.ProductMapper;
import com.flowledger.product.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@Transactional
public class ProductService extends OrganizationScopedService {
    private final ProductRepository repo;
    private final ProductMapper mapper;

    public ProductService(ProductRepository repo, ProductMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    public Response create(Create dto) {
        UUID org = orgId();
        if (repo.existsByOrganizationIdAndSku(org, dto.sku())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "SKU already exists");
        }
        Product product = mapper.toEntity(dto);
        product.setOrganizationId(org);
        if (dto.itemType() != null) {
            product.setItemType(dto.itemType());
        }
        return mapper.toResponse(repo.save(product));
    }

    @Transactional(readOnly = true)
    public Response get(UUID id) {
        return mapper.toResponse(load(id));
    }

    public Response update(UUID id, Update dto) {
        Product product = load(id);
        mapper.update(dto, product);
        if (dto.active() != null) {
            product.setActive(dto.active());
        }
        return mapper.toResponse(repo.save(product));
    }

    @Transactional(readOnly = true)
    public Page<Response> search(Search filter, Pageable pageable) {
        UUID org = orgId();
        Specification<Product> spec = (root, query, builder) -> builder.equal(root.get("organizationId"), org);
        if (filter.active() != null) {
            spec = spec.and((root, query, builder) -> builder.equal(root.get("active"), filter.active()));
        }
        if (filter.search() != null && !filter.search().isBlank()) {
            String pattern = "%" + filter.search().toLowerCase() + "%";
            spec = spec.and((root, query, builder) -> builder.or(
                    builder.like(builder.lower(root.get("name")), pattern),
                    builder.like(builder.lower(root.get("sku")), pattern),
                    builder.like(builder.lower(root.get("barcode")), pattern)
            ));
        }
        return repo.findAll(spec, pageable).map(mapper::toResponse);
    }

    private Product load(UUID id) {
        return required(repo.findByIdAndOrganizationId(id, orgId()), "Product");
    }
}
