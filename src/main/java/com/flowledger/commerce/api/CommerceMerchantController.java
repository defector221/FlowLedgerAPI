package com.flowledger.commerce.api;

import com.flowledger.commerce.dto.CommerceDtos;
import com.flowledger.commerce.service.MerchantProfileService;
import com.flowledger.common.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/commerce/merchant")
public class CommerceMerchantController {
    private final MerchantProfileService merchantService;

    public CommerceMerchantController(MerchantProfileService merchantService) {
        this.merchantService = merchantService;
    }

    @GetMapping("/profile")
    @PreAuthorize("hasAuthority('COMMERCE_VIEW')")
    public ApiResponse<CommerceDtos.MerchantProfileResponse> profile() {
        return ApiResponse.of(merchantService.getProfile());
    }

    @PutMapping("/integration")
    @PreAuthorize("hasAnyAuthority('COMMERCE_CONFIG_WRITE', 'COMMERCE_ADMIN')")
    public ApiResponse<CommerceDtos.IntegrationProfileResponse> updateIntegration(
            @Valid @RequestBody CommerceDtos.UpdateIntegrationRequest request) {
        return ApiResponse.of(merchantService.updateIntegration(request));
    }

    @PutMapping("/capabilities")
    @PreAuthorize("hasAnyAuthority('COMMERCE_CONFIG_WRITE', 'COMMERCE_ADMIN')")
    public ApiResponse<CommerceDtos.CapabilityUpdateResponse> updateCapabilities(
            @Valid @RequestBody CommerceDtos.UpdateCapabilitiesRequest request) {
        return ApiResponse.of(merchantService.updateCapabilities(request));
    }
}
