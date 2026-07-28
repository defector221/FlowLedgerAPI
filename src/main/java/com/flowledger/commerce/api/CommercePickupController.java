package com.flowledger.commerce.api;

import com.flowledger.commerce.dto.CommerceDtos;
import com.flowledger.commerce.fulfillment.FulfillmentService;
import com.flowledger.common.dto.ApiResponse;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/commerce/pickup")
public class CommercePickupController {
    private final FulfillmentService fulfillment;

    public CommercePickupController(FulfillmentService fulfillment) {
        this.fulfillment = fulfillment;
    }

    @PostMapping("/arrived")
    @PreAuthorize("hasAuthority('COMMERCE_FULFILLMENT_MANAGE')")
    public ApiResponse<CommerceDtos.FulfillmentOrderResponse> arrived(
            @RequestBody CommerceDtos.AcceptFulfillmentRequest request) {
        return ApiResponse.of(fulfillment.customerArrived(request.fulfillmentOrderId()));
    }

    @PostMapping("/verify")
    @PreAuthorize("hasAuthority('COMMERCE_FULFILLMENT_MANAGE')")
    public ApiResponse<CommerceDtos.FulfillmentOrderResponse> verifyPickup(
            @RequestBody CommerceDtos.AcceptFulfillmentRequest request) {
        return ApiResponse.of(fulfillment.verifyPickup(request.fulfillmentOrderId()));
    }

    @PostMapping("/collect/verify")
    @PreAuthorize("hasAuthority('COMMERCE_FULFILLMENT_MANAGE')")
    public ApiResponse<CommerceDtos.FulfillmentOrderResponse> verifyCollect(
            @RequestBody CommerceDtos.VerifyCollectRequest request) {
        return ApiResponse.of(fulfillment.verifyCollect(request));
    }
}
