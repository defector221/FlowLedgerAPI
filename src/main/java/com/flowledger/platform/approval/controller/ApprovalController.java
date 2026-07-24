package com.flowledger.platform.approval.controller;

import com.flowledger.common.dto.ApiResponse;
import com.flowledger.common.dto.PageResponse;
import com.flowledger.platform.approval.domain.ApprovalStatus;
import com.flowledger.platform.approval.dto.ApprovalDtos.*;
import com.flowledger.platform.approval.service.ApprovalEngineService;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/approvals")
public class ApprovalController {
    private final ApprovalEngineService service;

    public ApprovalController(ApprovalEngineService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('APPROVAL_READ') or hasRole('ORGANIZATION_ADMIN')")
    public ApiResponse<PageResponse<InstanceResponse>> inbox(
            @RequestParam(required = false) ApprovalStatus status, @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.of(PageResponse.from(service.inbox(status, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('APPROVAL_READ') or hasRole('ORGANIZATION_ADMIN')")
    public ApiResponse<InstanceResponse> get(@PathVariable UUID id) {
        return ApiResponse.of(service.get(id));
    }

    @PostMapping("/submit")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('APPROVAL_WRITE') or hasRole('ORGANIZATION_ADMIN')")
    public ApiResponse<InstanceResponse> submit(@RequestBody SubmitRequest request) {
        return ApiResponse.of(service.submit(request));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('APPROVAL_WRITE') or hasRole('ORGANIZATION_ADMIN')")
    public ApiResponse<InstanceResponse> approve(
            @PathVariable UUID id, @RequestBody(required = false) DecideRequest request) {
        return ApiResponse.of(service.approve(id, request == null ? new DecideRequest(null) : request));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('APPROVAL_WRITE') or hasRole('ORGANIZATION_ADMIN')")
    public ApiResponse<InstanceResponse> reject(
            @PathVariable UUID id, @RequestBody(required = false) DecideRequest request) {
        return ApiResponse.of(service.reject(id, request == null ? new DecideRequest(null) : request));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('APPROVAL_WRITE') or hasRole('ORGANIZATION_ADMIN')")
    public ApiResponse<InstanceResponse> cancel(
            @PathVariable UUID id, @RequestBody(required = false) DecideRequest request) {
        return ApiResponse.of(service.cancel(id, request == null ? new DecideRequest(null) : request));
    }

    @GetMapping("/definitions")
    @PreAuthorize("hasAuthority('APPROVAL_READ') or hasRole('ORGANIZATION_ADMIN')")
    public ApiResponse<List<DefinitionResponse>> definitions(@RequestParam String entityType) {
        return ApiResponse.of(service.listDefinitions(entityType));
    }

    @PostMapping("/definitions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ORGANIZATION_ADMIN')")
    public ApiResponse<DefinitionResponse> createDefinition(@RequestBody DefinitionRequest request) {
        return ApiResponse.of(service.upsertDefinition(request));
    }
}
