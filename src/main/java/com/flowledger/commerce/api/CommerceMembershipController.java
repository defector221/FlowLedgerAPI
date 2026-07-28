package com.flowledger.commerce.api;

import com.flowledger.commerce.dto.CommerceDtos;
import com.flowledger.commerce.service.CommerceCustomerService;
import com.flowledger.common.dto.ApiResponse;
import com.flowledger.common.dto.PageResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/commerce/memberships")
public class CommerceMembershipController {
    private final CommerceCustomerService customerService;

    public CommerceMembershipController(CommerceCustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('COMMERCE_VIEW')")
    public ApiResponse<PageResponse<CommerceDtos.MembershipResponse>> list(Pageable pageable) {
        var page = customerService.listMemberships(pageable);
        return ApiResponse.of(PageResponse.from(page));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('COMMERCE_ADMIN')")
    public ApiResponse<CommerceDtos.MembershipResponse> updateStatus(
            @PathVariable UUID id, @Valid @RequestBody CommerceDtos.UpdateMembershipStatusRequest request) {
        return ApiResponse.of(customerService.updateMembershipStatus(id, request.status()));
    }
}
