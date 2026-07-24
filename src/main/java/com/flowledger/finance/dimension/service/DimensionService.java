package com.flowledger.finance.dimension.service;

import com.flowledger.common.exception.ConflictException;
import com.flowledger.common.exception.ResourceNotFoundException;
import com.flowledger.common.tenant.TenantContext;
import com.flowledger.finance.dimension.dto.DimensionDtos.DimensionRequest;
import com.flowledger.finance.dimension.dto.DimensionDtos.DimensionResponse;
import com.flowledger.finance.dimension.entity.CostCenter;
import com.flowledger.finance.dimension.entity.Department;
import com.flowledger.finance.dimension.repository.CostCenterRepository;
import com.flowledger.finance.dimension.repository.DepartmentRepository;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DimensionService {
    private final CostCenterRepository costCenters;
    private final DepartmentRepository departments;

    public DimensionService(CostCenterRepository costCenters, DepartmentRepository departments) {
        this.costCenters = costCenters;
        this.departments = departments;
    }

    @Transactional(readOnly = true)
    public List<DimensionResponse> listCostCenters() {
        return costCenters.findByOrganizationIdOrderByCodeAsc(TenantContext.getOrganizationId()).stream()
                .map(c -> new DimensionResponse(c.getId(), c.getCode(), c.getName(), c.isActive()))
                .toList();
    }

    @Transactional
    public DimensionResponse createCostCenter(DimensionRequest request) {
        UUID org = TenantContext.getOrganizationId();
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (costCenters.existsByOrganizationIdAndCode(org, code)) {
            throw new ConflictException("Cost center code already exists");
        }
        CostCenter entity = new CostCenter();
        entity.setOrganizationId(org);
        entity.setCode(code);
        entity.setName(request.name().trim());
        if (request.active() != null) {
            entity.setActive(request.active());
        }
        CostCenter saved = costCenters.save(entity);
        return new DimensionResponse(saved.getId(), saved.getCode(), saved.getName(), saved.isActive());
    }

    @Transactional
    public DimensionResponse updateCostCenter(UUID id, DimensionRequest request) {
        CostCenter entity = costCenters
                .findByIdAndOrganizationId(id, TenantContext.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Cost center not found: " + id));
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (!code.equalsIgnoreCase(entity.getCode())
                && costCenters.existsByOrganizationIdAndCode(entity.getOrganizationId(), code)) {
            throw new ConflictException("Cost center code already exists");
        }
        entity.setCode(code);
        entity.setName(request.name().trim());
        if (request.active() != null) {
            entity.setActive(request.active());
        }
        CostCenter saved = costCenters.save(entity);
        return new DimensionResponse(saved.getId(), saved.getCode(), saved.getName(), saved.isActive());
    }

    @Transactional(readOnly = true)
    public List<DimensionResponse> listDepartments() {
        return departments.findByOrganizationIdOrderByCodeAsc(TenantContext.getOrganizationId()).stream()
                .map(d -> new DimensionResponse(d.getId(), d.getCode(), d.getName(), d.isActive()))
                .toList();
    }

    @Transactional
    public DimensionResponse createDepartment(DimensionRequest request) {
        UUID org = TenantContext.getOrganizationId();
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (departments.existsByOrganizationIdAndCode(org, code)) {
            throw new ConflictException("Department code already exists");
        }
        Department entity = new Department();
        entity.setOrganizationId(org);
        entity.setCode(code);
        entity.setName(request.name().trim());
        if (request.active() != null) {
            entity.setActive(request.active());
        }
        Department saved = departments.save(entity);
        return new DimensionResponse(saved.getId(), saved.getCode(), saved.getName(), saved.isActive());
    }

    @Transactional
    public DimensionResponse updateDepartment(UUID id, DimensionRequest request) {
        Department entity = departments
                .findByIdAndOrganizationId(id, TenantContext.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Department not found: " + id));
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (!code.equalsIgnoreCase(entity.getCode())
                && departments.existsByOrganizationIdAndCode(entity.getOrganizationId(), code)) {
            throw new ConflictException("Department code already exists");
        }
        entity.setCode(code);
        entity.setName(request.name().trim());
        if (request.active() != null) {
            entity.setActive(request.active());
        }
        Department saved = departments.save(entity);
        return new DimensionResponse(saved.getId(), saved.getCode(), saved.getName(), saved.isActive());
    }
}
