package com.flowledger.commerce.api;

import com.flowledger.commerce.dto.CommerceDtos;
import com.flowledger.commerce.fulfillment.FulfillmentService;
import com.flowledger.common.dto.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/commerce/delivery")
public class CommerceDeliveryController {
    private final FulfillmentService fulfillment;

    public CommerceDeliveryController(FulfillmentService fulfillment) {
        this.fulfillment = fulfillment;
    }

    @PostMapping("/assign")
    @PreAuthorize("hasAuthority('COMMERCE_FULFILLMENT_MANAGE')")
    public ApiResponse<CommerceDtos.FulfillmentOrderResponse> assign(
            @RequestBody CommerceDtos.AssignDriverRequest request) {
        return ApiResponse.of(fulfillment.assignDriver(request));
    }

    @PostMapping("/dispatch")
    @PreAuthorize("hasAuthority('COMMERCE_FULFILLMENT_MANAGE')")
    public ApiResponse<CommerceDtos.FulfillmentOrderResponse> dispatch(
            @RequestBody CommerceDtos.AcceptFulfillmentRequest request) {
        return ApiResponse.of(fulfillment.dispatchDelivery(request.fulfillmentOrderId()));
    }

    @PostMapping("/delivered")
    @PreAuthorize("hasAuthority('COMMERCE_FULFILLMENT_MANAGE')")
    public ApiResponse<CommerceDtos.FulfillmentOrderResponse> delivered(
            @RequestBody CommerceDtos.AcceptFulfillmentRequest request) {
        return ApiResponse.of(fulfillment.markDelivered(request.fulfillmentOrderId()));
    }
}
