package com.flowledger.ops.auth;

import com.flowledger.common.dto.ApiResponse;
import com.flowledger.ops.entity.PlatformUser;
import com.flowledger.ops.repository.PlatformUserRepository;
import com.flowledger.ops.security.PlatformJwtService;
import com.flowledger.ops.security.PlatformPrincipal;
import com.flowledger.ops.security.PlatformUserDetailsService;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/ops/auth")
public class PlatformAuthController {
    private final PlatformUserRepository users;
    private final PasswordEncoder encoder;
    private final PlatformJwtService jwt;
    private final PlatformUserDetailsService userDetails;
    private final com.flowledger.iam.config.IamProperties iamProperties;

    public PlatformAuthController(
            PlatformUserRepository users,
            PasswordEncoder encoder,
            PlatformJwtService jwt,
            PlatformUserDetailsService userDetails,
            com.flowledger.iam.config.IamProperties iamProperties) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.userDetails = userDetails;
        this.iamProperties = iamProperties;
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

    public record RefreshRequest(@NotBlank String refreshToken) {}

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody LoginRequest request) {
        if (iamProperties.isEnabled()) {
            throw new ResponseStatusException(
                    HttpStatus.GONE, "Password authentication is disabled. Use Sankhya IAM SSO (/api/v1/ops/auth/iam).");
        }
        PlatformUser user = users.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        if (!user.isActive() || !encoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        user.setLastLoginAt(OffsetDateTime.now());
        users.save(user);
        PlatformPrincipal principal = userDetails.toPrincipal(user);
        return ApiResponse.of(tokenPayload(principal));
    }

    @PostMapping("/refresh")
    public ApiResponse<Map<String, Object>> refresh(@RequestBody RefreshRequest request) {
        if (iamProperties.isEnabled()) {
            throw new ResponseStatusException(
                    HttpStatus.GONE, "Use /api/v1/ops/auth/iam/refresh while IAM SSO is enabled");
        }
        if (!jwt.isValidPlatformRefresh(request.refreshToken())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
        }
        PlatformPrincipal principal = userDetails.load(jwt.userId(request.refreshToken()));
        return ApiResponse.of(tokenPayload(principal));
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me(@AuthenticationPrincipal PlatformPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return ApiResponse.of(userMap(principal));
    }

    private Map<String, Object> tokenPayload(PlatformPrincipal principal) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("accessToken", jwt.createAccessToken(principal));
        body.put("refreshToken", jwt.createRefreshToken(principal));
        body.put("user", userMap(principal));
        return body;
    }

    private static Map<String, Object> userMap(PlatformPrincipal principal) {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", principal.getId());
        user.put("email", principal.getEmail());
        user.put("roles", List.copyOf(principal.getRoles()));
        user.put("permissions", List.copyOf(principal.getPermissions()));
        return user;
    }
}
