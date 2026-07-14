package com.flowledger.product.service;

import com.flowledger.common.service.OrganizationScopedService;
import com.flowledger.product.dto.CategoryDtos.*;
import com.flowledger.product.entity.Category;
import com.flowledger.product.mapper.CategoryMapper;
import com.flowledger.product.repository.CategoryRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
        String name = requireName(dto.name());
        assertUniqueName(org, name, null);
        if (dto.parentId() != null) {
            load(dto.parentId());
        }
        Category category = mapper.toEntity(dto);
        category.setName(name);
        category.setOrganizationId(org);
        return toResponse(repo.save(category));
    }

    @Transactional(readOnly = true)
    public Response get(UUID id) {
        return toResponse(load(id));
    }

    public Response update(UUID id, Update dto) {
        UUID org = orgId();
        Category category = load(id);
        String name = requireName(dto.name());
        assertUniqueName(org, name, id);
        if (dto.parentId() != null) {
            if (dto.parentId().equals(id)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category cannot be its own parent");
            }
            load(dto.parentId());
        }
        mapper.update(dto, category);
        category.setName(name);
        if (dto.active() != null) {
            category.setActive(dto.active());
        }
        return toResponse(repo.save(category));
    }

    @Transactional(readOnly = true)
    public List<Response> list() {
        Specification<Category> spec = (root, query, builder) -> builder.equal(root.get("organizationId"), orgId());
        List<Category> rows = repo.findAll(spec);
        Map<UUID, String> namesById = rows.stream()
                .collect(Collectors.toMap(Category::getId, Category::getName, (left, right) -> left));
        return rows.stream().map(category -> toResponse(category, namesById)).toList();
    }

    private Response toResponse(Category category) {
        String parentName = null;
        if (category.getParentId() != null) {
            parentName = repo.findByIdAndOrganizationId(category.getParentId(), orgId())
                    .map(Category::getName)
                    .orElse(null);
        }
        Response base = mapper.toResponse(category);
        return new Response(base.id(), base.name(), base.description(), base.parentId(), parentName, base.active());
    }

    private Response toResponse(Category category, Map<UUID, String> namesById) {
        Response base = mapper.toResponse(category);
        String parentName = category.getParentId() == null ? null : namesById.get(category.getParentId());
        return new Response(base.id(), base.name(), base.description(), base.parentId(), parentName, base.active());
    }

    private void assertUniqueName(UUID org, String name, UUID excludeId) {
        boolean exists = excludeId == null
                ? repo.existsByOrganizationIdAndNameIgnoreCase(org, name)
                : repo.existsByOrganizationIdAndNameIgnoreCaseAndIdNot(org, name, excludeId);
        if (exists) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Category name already exists in this organization");
        }
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category name is required");
        }
        return name.trim();
    }

    private Category load(UUID id) {
        return required(repo.findByIdAndOrganizationId(id, orgId()), "Category");
    }
}
