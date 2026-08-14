package com.flowledger.auth.controller;

import com.flowledger.auth.dto.*;
import com.flowledger.auth.service.AuthService;
import com.flowledger.auth.service.UserService;
import com.flowledger.common.dto.ApiResponse;
import com.flowledger.common.security.SecurityUtils;
import com.flowledger.iam.config.IamProperties;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService service;
    private final UserService userService;
    private final IamProperties iamProperties;

    public AuthController(AuthService service, UserService userService, IamProperties iamProperties) {
        this.service = service;
        this.userService = userService;
        this.iamProperties = iamProperties;
    }

    @PostMapping("/login")
    ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest r) {
        rejectWhenIamEnabled();
        return ApiResponse.of(service.login(r));
    }

    @PostMapping("/refresh")
    ApiResponse<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest r) {
        rejectWhenIamEnabled();
        return ApiResponse.of(service.refresh(r));
    }

    @PostMapping("/logout")
    ApiResponse<Void> logout(@Valid @RequestBody RefreshTokenRequest r) {
        if (!iamProperties.isEnabled()) {
            service.logout(r.refreshToken());
        }
        return ApiResponse.of(null);
    }

    @PostMapping("/forgot-password")
    ApiResponse<Void> forgot(@Valid @RequestBody ForgotPasswordRequest r) {
        rejectWhenIamEnabled();
        service.forgotPassword(r);
        return ApiResponse.of(null);
    }

    @PostMapping("/reset-password")
    ApiResponse<Void> reset(@Valid @RequestBody ResetPasswordRequest r) {
        rejectWhenIamEnabled();
        service.resetPassword(r);
        return ApiResponse.of(null);
    }

    @PostMapping("/change-password")
    ApiResponse<Void> change(@Valid @RequestBody ChangePasswordRequest r) {
        rejectWhenIamEnabled();
        service.changePassword(SecurityUtils.currentUserId(), r);
        return ApiResponse.of(null);
    }

    @PostMapping("/register")
    ApiResponse<LoginResponse> register(@Valid @RequestBody RegisterOrganizationRequest r) {
        rejectWhenIamEnabled();
        return ApiResponse.of(service.registerOrganization(r));
    }

    @GetMapping("/invitation")
    ApiResponse<UserDtos.InvitationPreviewResponse> previewInvitation(@RequestParam String token) {
        return ApiResponse.of(userService.previewInvitation(token));
    }

    @PostMapping("/accept-invitation")
    ApiResponse<Void> acceptInvitation(@Valid @RequestBody UserDtos.AcceptInvitationRequest request) {
        rejectWhenIamEnabled();
        userService.acceptInvitation(request);
        return ApiResponse.of(null, "Invitation accepted");
    }

    @PostMapping("/switch-organization")
    ApiResponse<LoginResponse> switchOrganization(@Valid @RequestBody SwitchOrganizationRequest request) {
        rejectWhenIamEnabled();
        return ApiResponse.of(service.switchOrganization(SecurityUtils.currentUserId(), request));
    }

    @PostMapping("/create-organization")
    ApiResponse<LoginResponse> createOrganization(@Valid @RequestBody CreateOrganizationRequest request) {
        rejectWhenIamEnabled();
        return ApiResponse.of(service.createOrganization(SecurityUtils.currentUserId(), request));
    }

    private void rejectWhenIamEnabled() {
        if (iamProperties.isEnabled()) {
            throw new ResponseStatusException(
                    HttpStatus.GONE, "Password authentication is disabled. Use Sankhya IAM SSO (/api/v1/auth/iam).");
        }
    }
}
