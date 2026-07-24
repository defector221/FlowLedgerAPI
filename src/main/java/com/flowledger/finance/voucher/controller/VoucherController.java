package com.flowledger.finance.voucher.controller;

import com.flowledger.common.dto.ApiResponse;
import com.flowledger.common.dto.PageResponse;
import com.flowledger.finance.voucher.domain.VoucherStatus;
import com.flowledger.finance.voucher.domain.VoucherType;
import com.flowledger.finance.voucher.dto.VoucherDtos.*;
import com.flowledger.finance.voucher.service.PostingEngine;
import com.flowledger.finance.voucher.service.VoucherService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/vouchers")
@Tag(name = "Finance — Vouchers")
public class VoucherController {
    private final VoucherService vouchers;
    private final PostingEngine posting;

    public VoucherController(VoucherService vouchers, PostingEngine posting) {
        this.vouchers = vouchers;
        this.posting = posting;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('VOUCHER_READ') or hasAnyRole('ORGANIZATION_ADMIN', 'ACCOUNTANT')")
    public ApiResponse<PageResponse<VoucherResponse>> list(
            @RequestParam(required = false) VoucherType type,
            @RequestParam(required = false) VoucherStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID branchId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.of(PageResponse.from(vouchers.list(type, status, from, to, search, branchId, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('VOUCHER_READ') or hasAnyRole('ORGANIZATION_ADMIN', 'ACCOUNTANT')")
    public ApiResponse<VoucherResponse> get(@PathVariable UUID id) {
        return ApiResponse.of(vouchers.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('VOUCHER_WRITE') or hasAnyRole('ORGANIZATION_ADMIN', 'ACCOUNTANT')")
    public ApiResponse<VoucherResponse> create(@Valid @RequestBody VoucherRequest request) {
        return ApiResponse.of(vouchers.createDraft(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('VOUCHER_WRITE') or hasAnyRole('ORGANIZATION_ADMIN', 'ACCOUNTANT')")
    public ApiResponse<VoucherResponse> update(@PathVariable UUID id, @Valid @RequestBody VoucherRequest request) {
        return ApiResponse.of(vouchers.updateDraft(id, request));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('VOUCHER_APPROVE') or hasAnyRole('ORGANIZATION_ADMIN', 'ACCOUNTANT')")
    public ApiResponse<VoucherResponse> approve(@PathVariable UUID id) {
        return ApiResponse.of(vouchers.approve(id));
    }

    @PostMapping("/{id}/post")
    @PreAuthorize("hasAuthority('VOUCHER_POST') or hasAnyRole('ORGANIZATION_ADMIN', 'ACCOUNTANT')")
    public ApiResponse<PostingResult> post(@PathVariable UUID id) {
        return ApiResponse.of(posting.post(id));
    }

    @PostMapping("/{id}/reverse")
    @PreAuthorize("hasAuthority('VOUCHER_POST') or hasAnyRole('ORGANIZATION_ADMIN', 'ACCOUNTANT')")
    public ApiResponse<PostingResult> reverse(@PathVariable UUID id) {
        return ApiResponse.of(posting.reverse(id));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('VOUCHER_WRITE') or hasAnyRole('ORGANIZATION_ADMIN', 'ACCOUNTANT')")
    public ApiResponse<VoucherResponse> cancel(@PathVariable UUID id) {
        return ApiResponse.of(vouchers.cancel(id));
    }

    @PostMapping("/{id}/duplicate")
    @PreAuthorize("hasAuthority('VOUCHER_WRITE') or hasAnyRole('ORGANIZATION_ADMIN', 'ACCOUNTANT')")
    public ApiResponse<VoucherResponse> duplicate(@PathVariable UUID id) {
        return ApiResponse.of(vouchers.duplicate(id));
    }
}
