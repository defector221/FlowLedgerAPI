package com.flowledger.finance.dimension.controller;

import com.flowledger.common.dto.ApiResponse;
import com.flowledger.finance.dimension.dto.DimensionDtos.DimensionRequest;
import com.flowledger.finance.dimension.dto.DimensionDtos.DimensionResponse;
import com.flowledger.finance.dimension.service.DimensionService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/dimensions")
public class DimensionController {
    private final DimensionService service;

    public DimensionController(DimensionService service) {
        this.service = service;
    }

    @GetMapping("/cost-centers")
    @PreAuthorize("hasAuthority('DIMENSION_MANAGE') or hasAnyRole('ORGANIZATION_ADMIN', 'ACCOUNTANT')")
    public ApiResponse<List<DimensionResponse>> listCostCenters() {
        return ApiResponse.of(service.listCostCenters());
    }

    @PostMapping("/cost-centers")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('DIMENSION_MANAGE') or hasAnyRole('ORGANIZATION_ADMIN', 'ACCOUNTANT')")
    public ApiResponse<DimensionResponse> createCostCenter(@Valid @RequestBody DimensionRequest request) {
        return ApiResponse.of(service.createCostCenter(request));
    }

    @PutMapping("/cost-centers/{id}")
    @PreAuthorize("hasAuthority('DIMENSION_MANAGE') or hasAnyRole('ORGANIZATION_ADMIN', 'ACCOUNTANT')")
    public ApiResponse<DimensionResponse> updateCostCenter(
            @PathVariable UUID id, @Valid @RequestBody DimensionRequest request) {
        return ApiResponse.of(service.updateCostCenter(id, request));
    }

    @GetMapping("/departments")
    @PreAuthorize("hasAuthority('DIMENSION_MANAGE') or hasAnyRole('ORGANIZATION_ADMIN', 'ACCOUNTANT')")
    public ApiResponse<List<DimensionResponse>> listDepartments() {
        return ApiResponse.of(service.listDepartments());
    }

    @PostMapping("/departments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('DIMENSION_MANAGE') or hasAnyRole('ORGANIZATION_ADMIN', 'ACCOUNTANT')")
    public ApiResponse<DimensionResponse> createDepartment(@Valid @RequestBody DimensionRequest request) {
        return ApiResponse.of(service.createDepartment(request));
    }

    @PutMapping("/departments/{id}")
    @PreAuthorize("hasAuthority('DIMENSION_MANAGE') or hasAnyRole('ORGANIZATION_ADMIN', 'ACCOUNTANT')")
    public ApiResponse<DimensionResponse> updateDepartment(
            @PathVariable UUID id, @Valid @RequestBody DimensionRequest request) {
        return ApiResponse.of(service.updateDepartment(id, request));
    }
}
