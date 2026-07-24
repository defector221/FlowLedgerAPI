package com.flowledger.organization.service;

import com.flowledger.common.exception.ConflictException;
import com.flowledger.common.exception.ResourceNotFoundException;
import com.flowledger.common.tenant.TenantContext;
import com.flowledger.organization.dto.BranchDtos.BranchRequest;
import com.flowledger.organization.dto.BranchDtos.BranchResponse;
import com.flowledger.organization.entity.Branch;
import com.flowledger.organization.repository.BranchRepository;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
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
        if (Boolean.TRUE.equals(request.defaultBranch())) {
            branches.clearDefault(org);
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
        if (Boolean.TRUE.equals(request.defaultBranch())) {
            branches.clearDefault(branch.getOrganizationId());
        }
        apply(branch, request, code);
        return toResponse(branches.save(branch));
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
        if (request.active() != null) {
            branch.setActive(request.active());
        }
        if (request.defaultBranch() != null) {
            branch.setDefaultBranch(request.defaultBranch());
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
                branch.isActive(),
                branch.isDefaultBranch());
    }
}
