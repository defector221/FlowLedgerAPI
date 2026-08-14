package com.flowledger.iam.controller;

import com.flowledger.common.dto.ApiResponse;
import com.flowledger.iam.service.IamAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/ops/auth/iam")
public class PlatformIamAuthController {
    private final IamAuthService iamAuth;

    public PlatformIamAuthController(IamAuthService iamAuth) {
        this.iamAuth = iamAuth;
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        return ApiResponse.of(iamAuth.status());
    }

    @GetMapping("/login-url")
    public ApiResponse<Map<String, String>> loginUrl(HttpServletResponse response) {
        return ApiResponse.of(iamAuth.beginLogin(true, response));
    }

    @GetMapping("/callback")
    public void callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            HttpServletRequest request,
            HttpServletResponse response)
            throws IOException {
        if (error != null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "OIDC error: " + error);
        }
        String redirect = iamAuth.handleCallback(true, code, state, request, response);
        response.sendRedirect(redirect);
    }

    @PostMapping("/claim")
    public ApiResponse<Object> claim(@RequestBody IamAuthController.ClaimRequest body) {
        return ApiResponse.of(iamAuth.claim(true, body.code()));
    }

    @PostMapping("/refresh")
    public ApiResponse<Object> refresh(HttpServletRequest request, HttpServletResponse response) {
        return ApiResponse.of(iamAuth.refresh(true, request, response));
    }

    @GetMapping("/logout-url")
    public ApiResponse<Map<String, String>> logoutUrl(HttpServletRequest request, HttpServletResponse response) {
        return ApiResponse.of(iamAuth.logoutUrl(true, request, response));
    }
}
