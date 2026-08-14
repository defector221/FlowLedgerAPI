package com.flowledger.iam.controller;

import com.flowledger.common.dto.ApiResponse;
import com.flowledger.auth.dto.LoginResponse;
import com.flowledger.auth.dto.OrganizationAccessResponse;
import com.flowledger.iam.service.IamAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/auth/iam")
public class IamAuthController {
    private final IamAuthService iamAuth;

    public IamAuthController(IamAuthService iamAuth) {
        this.iamAuth = iamAuth;
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        return ApiResponse.of(iamAuth.status());
    }

    @GetMapping("/login-url")
    public ApiResponse<Map<String, String>> loginUrl(HttpServletResponse response) {
        return ApiResponse.of(iamAuth.beginLogin(false, response));
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
        String redirect = iamAuth.handleCallback(false, code, state, request, response);
        response.sendRedirect(redirect);
    }

    @PostMapping("/claim")
    public ApiResponse<Object> claim(@RequestBody ClaimRequest body) {
        return ApiResponse.of(iamAuth.claim(false, body.code()));
    }

    @PostMapping("/refresh")
    public ApiResponse<Object> refresh(HttpServletRequest request, HttpServletResponse response) {
        return ApiResponse.of(iamAuth.refresh(false, request, response));
    }

    @GetMapping("/logout-url")
    public ApiResponse<Map<String, String>> logoutUrl(HttpServletRequest request, HttpServletResponse response) {
        return ApiResponse.of(iamAuth.logoutUrl(false, request, response));
    }

    @GetMapping("/organizations")
    public ApiResponse<List<OrganizationAccessResponse>> organizations(HttpServletRequest request) {
        return ApiResponse.of(iamAuth.listOrganizations(request));
    }

    @PostMapping("/organizations/{organizationId}/switch")
    public ApiResponse<LoginResponse> switchOrganization(
            @PathVariable UUID organizationId, HttpServletRequest request) {
        return ApiResponse.of(iamAuth.switchOrganization(organizationId, request));
    }

    @GetMapping("/context")
    public ApiResponse<Map<String, Object>> context(HttpServletRequest request) {
        return ApiResponse.of(iamAuth.currentContext(request));
    }

    @PostMapping("/signup")
    public ApiResponse<Map<String, Object>> signup(
            @RequestBody SignupRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request) {
        String clientIp = request.getHeader("X-Forwarded-For");
        if (clientIp == null || clientIp.isBlank()) {
            clientIp = request.getRemoteAddr();
        } else {
            clientIp = clientIp.split(",")[0].trim();
        }
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("email", body.email());
        payload.put("displayName", body.displayName());
        payload.put("organizationName", body.organizationName());
        payload.put("organizationSlug", body.organizationSlug());
        payload.put("acceptedTerms", Boolean.TRUE.equals(body.acceptedTerms()));
        payload.put("captchaToken", body.captchaToken());
        var result = iamAuth.signup(payload, idempotencyKey, clientIp);
        return ApiResponse.of(Map.of(
                "signupId", result.signupId() != null ? result.signupId() : "",
                "status", result.status() != null ? result.status() : "",
                "message", result.message() != null ? result.message() : ""));
    }

    @GetMapping("/availability")
    public ApiResponse<Map<String, Object>> availability(
            @RequestParam String type, @RequestParam String value) {
        var result = iamAuth.checkAvailability(type, value);
        return ApiResponse.of(Map.of(
                "type", result.type() != null ? result.type() : "",
                "value", result.value() != null ? result.value() : "",
                "available", Boolean.TRUE.equals(result.available()),
                "definite", Boolean.TRUE.equals(result.definite()),
                "checkedVia", result.checkedVia() != null ? result.checkedVia() : "",
                "bloomMaybeTaken", Boolean.TRUE.equals(result.bloomMaybeTaken())));
    }

    @GetMapping("/availability/probe")
    public ApiResponse<Map<String, Object>> availabilityProbe(
            @RequestParam String type, @RequestParam String value) {
        var result = iamAuth.probeAvailability(type, value);
        return ApiResponse.of(Map.of(
                "type", result.type() != null ? result.type() : "",
                "value", result.value() != null ? result.value() : "",
                "maybeTaken", Boolean.TRUE.equals(result.maybeTaken())));
    }

    public record ClaimRequest(String code) {}

    public record SignupRequest(
            String email,
            String displayName,
            String organizationName,
            String organizationSlug,
            Boolean acceptedTerms,
            String captchaToken) {}
}
