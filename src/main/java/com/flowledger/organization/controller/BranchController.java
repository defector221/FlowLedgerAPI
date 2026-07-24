package com.flowledger.organization.controller;

import com.flowledger.common.dto.ApiResponse;
import com.flowledger.organization.dto.BranchDtos.BranchRequest;
import com.flowledger.organization.dto.BranchDtos.BranchResponse;
import com.flowledger.organization.service.BranchService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/branches")
public class BranchController {
    private final BranchService service;

    public BranchController(BranchService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('BRANCH_MANAGE') or hasAnyRole('ORGANIZATION_ADMIN', 'ACCOUNTANT')")
    public ApiResponse<List<BranchResponse>> list() {
        return ApiResponse.of(service.list());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('BRANCH_MANAGE') or hasAnyRole('ORGANIZATION_ADMIN', 'ACCOUNTANT')")
    public ApiResponse<BranchResponse> get(@PathVariable UUID id) {
        return ApiResponse.of(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('BRANCH_MANAGE') or hasRole('ORGANIZATION_ADMIN')")
    public ApiResponse<BranchResponse> create(@Valid @RequestBody BranchRequest request) {
        return ApiResponse.of(service.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('BRANCH_MANAGE') or hasRole('ORGANIZATION_ADMIN')")
    public ApiResponse<BranchResponse> update(@PathVariable UUID id, @Valid @RequestBody BranchRequest request) {
        return ApiResponse.of(service.update(id, request));
    }
}
