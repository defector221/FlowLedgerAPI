package com.flowledger.audit.controller;

import com.flowledger.audit.dto.AuditLogResponse;
import com.flowledger.audit.service.AuditService;
import com.flowledger.common.dto.ApiResponse;
import com.flowledger.common.dto.PageResponse;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit-logs")
public class AuditController {
    private final AuditService service;

    public AuditController(AuditService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('AUDIT_READ') or hasAuthority('REPORT_READ')")
    public ApiResponse<PageResponse<AuditLogResponse>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.of(
                PageResponse.from(service.list(search, action, entityType, userId, from, to, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('AUDIT_READ') or hasAuthority('REPORT_READ')")
    public ApiResponse<AuditLogResponse> get(@PathVariable UUID id) {
        return ApiResponse.of(service.get(id));
    }
}
