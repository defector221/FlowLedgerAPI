package com.flowledger.commerce.api;

import com.flowledger.commerce.dto.CommerceDtos;
import com.flowledger.commerce.order.OrderService;
import com.flowledger.common.dto.ApiResponse;
import com.flowledger.common.dto.PageResponse;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/commerce/orders")
@PreAuthorize("hasAuthority('COMMERCE_CUSTOMER')")
public class CommerceOrderController {
    private final OrderService orderService;

    public CommerceOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public ApiResponse<PageResponse<CommerceDtos.CommerceOrderResponse>> list(
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.of(orderService.listOrders(pageable));
    }

    @GetMapping("/{orderId}")
    public ApiResponse<CommerceDtos.CommerceOrderResponse> get(@PathVariable UUID orderId) {
        return ApiResponse.of(orderService.getOrder(orderId));
    }
}
