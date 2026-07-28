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
@RequestMapping("/api/v1/commerce/picking")
public class CommercePickingController {
    private final FulfillmentService fulfillment;

    public CommercePickingController(FulfillmentService fulfillment) {
        this.fulfillment = fulfillment;
    }

    @PostMapping("/start")
    @PreAuthorize("hasAuthority('COMMERCE_FULFILLMENT_MANAGE')")
    public ApiResponse<CommerceDtos.FulfillmentOrderResponse> start(
            @RequestBody CommerceDtos.FulfillmentTaskRequest request) {
        return ApiResponse.of(fulfillment.startPicking(request));
    }

    @PostMapping("/complete")
    @PreAuthorize("hasAuthority('COMMERCE_FULFILLMENT_MANAGE')")
    public ApiResponse<CommerceDtos.FulfillmentOrderResponse> complete(@RequestBody CommerceDtos.AcceptFulfillmentRequest request) {
        return ApiResponse.of(fulfillment.completePicking(request.fulfillmentOrderId()));
    }
}
