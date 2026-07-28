package com.flowledger.commerce.api;

import com.flowledger.commerce.dto.CommerceDtos;
import com.flowledger.commerce.fulfillment.FulfillmentService;
import com.flowledger.commerce.fulfillment.delivery.SlotBookingService;
import com.flowledger.common.dto.ApiResponse;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/commerce/fulfillment")
public class CommerceFulfillmentController {
    private final FulfillmentService fulfillment;
    private final SlotBookingService slots;

    public CommerceFulfillmentController(FulfillmentService fulfillment, SlotBookingService slots) {
        this.fulfillment = fulfillment;
        this.slots = slots;
    }

    @GetMapping("/orders")
    @PreAuthorize("hasAuthority('COMMERCE_FULFILLMENT_VIEW')")
    public ApiResponse<java.util.List<CommerceDtos.FulfillmentOrderResponse>> list(
            @RequestParam UUID storeId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String fulfillmentTypes) {
        return ApiResponse.of(fulfillment.listByStore(storeId, status, fulfillmentTypes));
    }

    @GetMapping("/orders/{fulfillmentOrderId}")
    @PreAuthorize("hasAuthority('COMMERCE_FULFILLMENT_VIEW')")
    public ApiResponse<CommerceDtos.FulfillmentOrderDetailResponse> get(
            @PathVariable UUID fulfillmentOrderId) {
        return ApiResponse.of(fulfillment.getDetail(fulfillmentOrderId));
    }

    @PostMapping("/orders/{fulfillmentOrderId}/returns")
    @PreAuthorize("hasAuthority('COMMERCE_FULFILLMENT_MANAGE')")
    public ApiResponse<CommerceDtos.CommerceOrderReturnResponse> processReturn(
            @PathVariable UUID fulfillmentOrderId,
            @RequestBody CommerceDtos.CreateCommerceReturnRequest request) {
        return ApiResponse.of(fulfillment.processReturn(fulfillmentOrderId, request));
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('COMMERCE_FULFILLMENT_VIEW')")
    public ApiResponse<CommerceDtos.FulfillmentDashboardResponse> dashboard(@RequestParam UUID storeId) {
        return ApiResponse.of(fulfillment.dashboard(storeId));
    }

    @PostMapping("/accept")
    @PreAuthorize("hasAuthority('COMMERCE_FULFILLMENT_MANAGE')")
    public ApiResponse<CommerceDtos.FulfillmentOrderResponse> accept(
            @RequestBody CommerceDtos.AcceptFulfillmentRequest request) {
        return ApiResponse.of(fulfillment.accept(request));
    }

    @GetMapping("/slots")
    @PreAuthorize("hasAuthority('COMMERCE_FULFILLMENT_VIEW')")
    public ApiResponse<java.util.List<CommerceDtos.FulfillmentSlotResponse>> slots(
            @RequestParam UUID storeId,
            @RequestParam(required = false) String type,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.of(slots.listSlots(storeId, type, date));
    }
}
