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
@RequestMapping("/api/v1/commerce/packing")
public class CommercePackingController {
    private final FulfillmentService fulfillment;

    public CommercePackingController(FulfillmentService fulfillment) {
        this.fulfillment = fulfillment;
    }

    @PostMapping("/start")
    @PreAuthorize("hasAuthority('COMMERCE_FULFILLMENT_MANAGE')")
    public ApiResponse<CommerceDtos.FulfillmentOrderResponse> start(
            @RequestBody CommerceDtos.FulfillmentTaskRequest request) {
        return ApiResponse.of(fulfillment.startPacking(request));
    }

    @PostMapping("/complete")
    @PreAuthorize("hasAuthority('COMMERCE_FULFILLMENT_MANAGE')")
    public ApiResponse<CommerceDtos.FulfillmentOrderResponse> complete(
            @RequestBody CommerceDtos.AcceptFulfillmentRequest request) {
        return ApiResponse.of(fulfillment.completePacking(request.fulfillmentOrderId()));
    }
}
