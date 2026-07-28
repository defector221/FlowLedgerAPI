package com.flowledger.commerce.api;

import com.flowledger.commerce.checkout.CheckoutService;
import com.flowledger.commerce.dto.CommerceDtos;
import com.flowledger.common.dto.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/commerce/checkout")
@PreAuthorize("hasAuthority('COMMERCE_CUSTOMER')")
public class CommerceCheckoutController {
    private final CheckoutService checkoutService;

    public CommerceCheckoutController(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    @PostMapping("/sessions")
    public ApiResponse<CommerceDtos.CheckoutSessionResponse> start(
            @Valid @RequestBody CommerceDtos.StartCheckoutRequest request) {
        return ApiResponse.of(checkoutService.startCheckout(request));
    }

    @GetMapping("/sessions/{sessionId}")
    public ApiResponse<CommerceDtos.CheckoutSessionResponse> get(@PathVariable UUID sessionId) {
        return ApiResponse.of(checkoutService.getSession(sessionId));
    }

    @PostMapping("/sessions/{sessionId}/coupon")
    public ApiResponse<CommerceDtos.CheckoutSessionResponse> applyCoupon(
            @PathVariable UUID sessionId, @Valid @RequestBody CommerceDtos.ApplyCheckoutCouponRequest request) {
        return ApiResponse.of(checkoutService.applyCoupon(sessionId, request));
    }

    @PostMapping("/sessions/{sessionId}/pay")
    public ApiResponse<CommerceDtos.PaymentSessionResponse> pay(
            @PathVariable UUID sessionId, @Valid @RequestBody CommerceDtos.InitiatePaymentRequest request) {
        return ApiResponse.of(checkoutService.initiatePayment(sessionId, request));
    }

    @PostMapping("/sessions/{sessionId}/confirm")
    public ApiResponse<CommerceDtos.CommerceOrderResponse> confirm(
            @PathVariable UUID sessionId, @RequestBody(required = false) CommerceDtos.ConfirmCheckoutRequest request) {
        CommerceDtos.ConfirmCheckoutRequest body =
                request != null ? request : new CommerceDtos.ConfirmCheckoutRequest(null, null);
        return ApiResponse.of(checkoutService.confirmCheckout(sessionId, body));
    }
}
