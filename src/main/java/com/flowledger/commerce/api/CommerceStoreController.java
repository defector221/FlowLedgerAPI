package com.flowledger.commerce.api;

import com.flowledger.commerce.dto.CommerceDtos;
import com.flowledger.commerce.service.StoreCommerceService;
import com.flowledger.common.dto.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/commerce/stores")
public class CommerceStoreController {
    private final StoreCommerceService storeCommerceService;

    public CommerceStoreController(StoreCommerceService storeCommerceService) {
        this.storeCommerceService = storeCommerceService;
    }

    @GetMapping("/{storeId}/commerce")
    @PreAuthorize("hasAuthority('COMMERCE_VIEW')")
    public ApiResponse<CommerceDtos.StoreCommerceResponse> get(@PathVariable UUID storeId) {
        return ApiResponse.of(storeCommerceService.get(storeId));
    }

    @PutMapping("/{storeId}/commerce")
    @PreAuthorize("hasAuthority('COMMERCE_STORE_MANAGE')")
    public ApiResponse<CommerceDtos.StoreCommerceResponse> update(
            @PathVariable UUID storeId, @Valid @RequestBody CommerceDtos.UpdateStoreCommerceRequest request) {
        return ApiResponse.of(storeCommerceService.update(storeId, request));
    }

    @PostMapping("/{storeId}/publish")
    @PreAuthorize("hasAuthority('COMMERCE_STORE_MANAGE')")
    public ApiResponse<CommerceDtos.PublishResultResponse> publish(@PathVariable UUID storeId) {
        return ApiResponse.of(storeCommerceService.publish(storeId));
    }

    @PostMapping("/{storeId}/unpublish")
    @PreAuthorize("hasAuthority('COMMERCE_STORE_MANAGE')")
    public ApiResponse<CommerceDtos.PublishResultResponse> unpublish(@PathVariable UUID storeId) {
        return ApiResponse.of(storeCommerceService.unpublish(storeId));
    }
}
