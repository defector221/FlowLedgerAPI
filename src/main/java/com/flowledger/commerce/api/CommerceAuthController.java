package com.flowledger.commerce.api;

import com.flowledger.commerce.auth.CommerceAuthService;
import com.flowledger.commerce.dto.CommerceDtos;
import com.flowledger.common.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/commerce/auth")
public class CommerceAuthController {
    private final CommerceAuthService authService;

    public CommerceAuthController(CommerceAuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/request-otp")
    public ApiResponse<CommerceDtos.RequestOtpResponse> requestOtp(@Valid @RequestBody CommerceDtos.RequestOtpRequest request) {
        return ApiResponse.of(authService.requestOtp(request.mobile()));
    }

    @PostMapping("/verify-otp")
    public ApiResponse<CommerceDtos.CommerceTokenResponse> verifyOtp(@Valid @RequestBody CommerceDtos.VerifyOtpRequest request) {
        return ApiResponse.of(authService.verifyOtp(request.mobile(), request.otp()));
    }

    @PostMapping("/refresh")
    public ApiResponse<CommerceDtos.CommerceTokenResponse> refresh(@Valid @RequestBody CommerceDtos.RefreshTokenRequest request) {
        return ApiResponse.of(authService.refresh(request.refreshToken()));
    }
}
