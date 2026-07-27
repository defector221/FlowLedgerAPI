package com.flowledger.organization.service;

import com.flowledger.common.dto.PageResponse;
import com.flowledger.common.exception.ConflictException;
import com.flowledger.common.exception.ResourceNotFoundException;
import com.flowledger.common.tenant.TenantContext;
import com.flowledger.organization.dto.BranchDtos.BranchRequest;
import com.flowledger.organization.dto.BranchDtos.BranchResponse;
import com.flowledger.organization.entity.Branch;
import com.flowledger.organization.repository.BranchRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BranchService {
    private final BranchRepository branches;

    public BranchService(BranchRepository branches) {
        this.branches = branches;
    }

    @Transactional(readOnly = true)
    public List<BranchResponse> list() {
        return branches.findByOrganizationIdOrderByNameAsc(TenantContext.getOrganizationId()).stream()
                .map(BranchService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<BranchResponse> search(String search, Boolean active, Pageable pageable) {
        UUID org = TenantContext.getOrganizationId();
        Specification<Branch> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("organizationId"), org));
            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("code")), pattern),
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(root.get("city")), pattern)));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        Page<Branch> page = branches.findAll(spec, pageable);
        return PageResponse.from(page.map(BranchService::toResponse));
    }

    @Transactional(readOnly = true)
    public BranchResponse get(UUID id) {
        return toResponse(load(id));
    }

    @Transactional
    public BranchResponse create(BranchRequest request) {
        UUID org = TenantContext.getOrganizationId();
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (branches.existsByOrganizationIdAndCode(org, code)) {
            throw new ConflictException("Branch code already exists");
        }
        if (Boolean.TRUE.equals(request.defaultBranch()) || Boolean.TRUE.equals(request.headOffice())) {
            branches.clearDefault(org);
            branches.clearHeadOffice(org);
        }
        Branch branch = new Branch();
        branch.setOrganizationId(org);
        apply(branch, request, code);
        return toResponse(branches.save(branch));
    }

    @Transactional
    public BranchResponse update(UUID id, BranchRequest request) {
        Branch branch = load(id);
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (!code.equalsIgnoreCase(branch.getCode())
                && branches.existsByOrganizationIdAndCode(branch.getOrganizationId(), code)) {
            throw new ConflictException("Branch code already exists");
        }
        if (Boolean.TRUE.equals(request.defaultBranch()) || Boolean.TRUE.equals(request.headOffice())) {
            branches.clearDefault(branch.getOrganizationId());
            branches.clearHeadOffice(branch.getOrganizationId());
        }
        apply(branch, request, code);
        return toResponse(branches.save(branch));
    }

    @Transactional
    public void deactivate(UUID id) {
        Branch branch = load(id);
        if (branch.isDefaultBranch()) {
            throw new ConflictException("Cannot deactivate the default branch");
        }
        branch.setActive(false);
        branches.save(branch);
    }

    @Transactional(readOnly = true)
    public Branch loadEntity(UUID id) {
        return load(id);
    }

    private Branch load(UUID id) {
        return branches.findByIdAndOrganizationId(id, TenantContext.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found: " + id));
    }

    private static void apply(Branch branch, BranchRequest request, String code) {
        branch.setCode(code);
        branch.setName(request.name().trim());
        branch.setAddressLine1(request.addressLine1());
        branch.setCity(request.city());
        branch.setState(request.state());
        branch.setPostalCode(request.postalCode());
        branch.setCountry(request.country() == null || request.country().isBlank() ? "IN" : request.country());
        branch.setGstNumber(request.gstNumber());
        branch.setPan(request.pan());
        branch.setPhone(request.phone());
        branch.setEmail(request.email());
        if (request.active() != null) {
            branch.setActive(request.active());
        }
        if (request.defaultBranch() != null) {
            branch.setDefaultBranch(request.defaultBranch());
        }
        if (request.headOffice() != null) {
            branch.setHeadOffice(request.headOffice());
            if (request.headOffice()) {
                branch.setDefaultBranch(true);
            }
        }
    }

    private static BranchResponse toResponse(Branch branch) {
        return new BranchResponse(
                branch.getId(),
                branch.getCode(),
                branch.getName(),
                branch.getAddressLine1(),
                branch.getCity(),
                branch.getState(),
                branch.getPostalCode(),
                branch.getCountry(),
                branch.getGstNumber(),
                branch.getPan(),
                branch.getPhone(),
                branch.getEmail(),
                branch.isActive(),
                branch.isDefaultBranch(),
                branch.isHeadOffice());
    }
}
