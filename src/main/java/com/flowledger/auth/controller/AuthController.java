package com.flowledger.auth.controller;

import com.flowledger.auth.dto.*;
import com.flowledger.auth.service.AuthService;
import com.flowledger.common.dto.ApiResponse;
import com.flowledger.common.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    @PostMapping("/login")
    ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest r) {
        return ApiResponse.of(service.login(r));
    }

    @PostMapping("/refresh")
    ApiResponse<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest r) {
        return ApiResponse.of(service.refresh(r));
    }

    @PostMapping("/logout")
    ApiResponse<Void> logout(@Valid @RequestBody RefreshTokenRequest r) {
        service.logout(r.refreshToken());
        return ApiResponse.of(null);
    }

    @PostMapping("/forgot-password")
    ApiResponse<Void> forgot(@Valid @RequestBody ForgotPasswordRequest r) {
        service.forgotPassword(r);
        return ApiResponse.of(null);
    }

    @PostMapping("/reset-password")
    ApiResponse<Void> reset(@Valid @RequestBody ResetPasswordRequest r) {
        service.resetPassword(r);
        return ApiResponse.of(null);
    }

    @PostMapping("/change-password")
    ApiResponse<Void> change(@Valid @RequestBody ChangePasswordRequest r) {
        service.changePassword(SecurityUtils.currentUserId(), r);
        return ApiResponse.of(null);
    }

    @PostMapping("/register")
    ApiResponse<LoginResponse> register(@Valid @RequestBody RegisterOrganizationRequest r) {
        return ApiResponse.of(service.registerOrganization(r));
    }
}
