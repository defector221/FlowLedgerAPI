package com.flowledger.product.service;

import com.flowledger.common.service.OrganizationScopedService;
import com.flowledger.product.dto.CategoryDtos.*;
import com.flowledger.product.entity.Category;
import com.flowledger.product.mapper.CategoryMapper;
import com.flowledger.product.repository.CategoryRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class CategoryService extends OrganizationScopedService {
    private final CategoryRepository repo;
    private final CategoryMapper mapper;

    public CategoryService(CategoryRepository repo, CategoryMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    public Response create(Create dto) {
        UUID org = orgId();
        if (repo.existsByOrganizationIdAndName(org, dto.name())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Category name already exists");
        }
        Category category = mapper.toEntity(dto);
        category.setOrganizationId(org);
        return mapper.toResponse(repo.save(category));
    }

    @Transactional(readOnly = true)
    public Response get(UUID id) {
        return mapper.toResponse(load(id));
    }

    public Response update(UUID id, Update dto) {
        Category category = load(id);
        mapper.update(dto, category);
        if (dto.active() != null) {
            category.setActive(dto.active());
        }
        return mapper.toResponse(repo.save(category));
    }

    @Transactional(readOnly = true)
    public List<Response> list() {
        Specification<Category> spec = (root, query, builder) -> builder.equal(root.get("organizationId"), orgId());
        return repo.findAll(spec).stream().map(mapper::toResponse).toList();
    }

    private Category load(UUID id) {
        return required(repo.findByIdAndOrganizationId(id, orgId()), "Category");
    }
}
