package com.flowledger.commerce.api;

import com.flowledger.commerce.dto.CommerceDtos;
import com.flowledger.commerce.service.CommerceCustomerService;
import com.flowledger.common.dto.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/commerce/customers")
public class CommerceCustomerController {
    private final CommerceCustomerService customerService;

    public CommerceCustomerController(CommerceCustomerService customerService) {
        this.customerService = customerService;
    }

    @PostMapping
    public ApiResponse<CommerceDtos.CustomerResponse> register(@Valid @RequestBody CommerceDtos.RegisterCustomerRequest request) {
        return ApiResponse.of(customerService.register(request));
    }

    @GetMapping("/me")
    @PreAuthorize("hasAuthority('COMMERCE_CUSTOMER')")
    public ApiResponse<CommerceDtos.CustomerResponse> me() {
        return ApiResponse.of(customerService.me());
    }

    @PutMapping("/me")
    @PreAuthorize("hasAuthority('COMMERCE_CUSTOMER')")
    public ApiResponse<CommerceDtos.CustomerResponse> updateMe(@Valid @RequestBody CommerceDtos.UpdateCustomerRequest request) {
        return ApiResponse.of(customerService.updateMe(request));
    }

    @GetMapping("/address")
    @PreAuthorize("hasAuthority('COMMERCE_CUSTOMER')")
    public ApiResponse<List<CommerceDtos.AddressResponse>> listAddresses() {
        return ApiResponse.of(customerService.listAddresses());
    }

    @PostMapping("/address")
    @PreAuthorize("hasAuthority('COMMERCE_CUSTOMER')")
    public ApiResponse<CommerceDtos.AddressResponse> addAddress(@Valid @RequestBody CommerceDtos.AddressRequest request) {
        return ApiResponse.of(customerService.addAddress(request));
    }

    @PutMapping("/address/{id}")
    @PreAuthorize("hasAuthority('COMMERCE_CUSTOMER')")
    public ApiResponse<CommerceDtos.AddressResponse> updateAddress(
            @PathVariable UUID id, @Valid @RequestBody CommerceDtos.AddressRequest request) {
        return ApiResponse.of(customerService.updateAddress(id, request));
    }

    @DeleteMapping("/address/{id}")
    @PreAuthorize("hasAuthority('COMMERCE_CUSTOMER')")
    public ApiResponse<Void> deleteAddress(@PathVariable UUID id) {
        customerService.deleteAddress(id);
        return ApiResponse.of(null);
    }
}
