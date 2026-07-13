package com.flowledger.auth.service;

import com.flowledger.auth.dto.*;
import com.flowledger.auth.entity.*;
import com.flowledger.auth.repository.*;
import com.flowledger.common.exception.*;
import com.flowledger.common.security.*;
import com.flowledger.organization.entity.*;
import com.flowledger.organization.repository.*;
import java.time.*;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class AuthService {
    private final UserRepository users;
    private final RoleRepository roles;
    private final RefreshTokenRepository refreshTokens;
    private final OrganizationRepository organizations;
    private final OrganizationSettingsRepository settings;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final CustomUserDetailsService details;
    private final OrganizationMembershipService membershipService;

    @Value("${flowledger.app.frontend-url}")
    private String frontendUrl;

    public AuthService(
            UserRepository users,
            RoleRepository roles,
            RefreshTokenRepository refreshTokens,
            OrganizationRepository organizations,
            OrganizationSettingsRepository settings,
            PasswordEncoder encoder,
            JwtService jwt,
            CustomUserDetailsService details,
            OrganizationMembershipService membershipService) {
        this.users = users;
        this.roles = roles;
        this.refreshTokens = refreshTokens;
        this.organizations = organizations;
        this.settings = settings;
        this.encoder = encoder;
        this.jwt = jwt;
        this.details = details;
        this.membershipService = membershipService;
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = users.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));
        if (!user.isActive() || !encoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid credentials");
        }
        if ("INVITED".equals(user.getUserStatus())) {
            throw new UnauthorizedException("Please accept your invitation before signing in");
        }
        if ("INACTIVE".equals(user.getUserStatus())) {
            throw new UnauthorizedException("Your account has been deactivated");
        }
        user.setLastLoginAt(Instant.now());
        OrganizationAccessResponse activeOrganization = membershipService.resolveActiveOrganization(user);
        membershipService.requireActiveMembership(user.getId(), activeOrganization.id());
        return tokens(user, activeOrganization.id());
    }

    @Transactional
    public LoginResponse refresh(RefreshTokenRequest request) {
        if (!jwt.isValid(request.refreshToken(), "refresh")) {
            throw new UnauthorizedException("Invalid refresh token");
        }
        String hash = hash(request.refreshToken());
        RefreshToken token = refreshTokens
                .findByTokenHashAndRevokedFalse(hash)
                .filter(existing -> existing.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));
        token.setRevoked(true);
        User user = users.findByIdAndActiveTrue(token.getUserId())
                .orElseThrow(() -> new UnauthorizedException("User unavailable"));
        UUID organizationId = jwt.organizationId(request.refreshToken());
        if (organizationId == null) {
            organizationId = membershipService.resolveActiveOrganization(user).id();
        }
        LoginResponse result = tokens(user, organizationId);
        token.setReplacedBy(refreshTokens
                .findByTokenHashAndRevokedFalse(hash(result.refreshToken()))
                .orElseThrow()
                .getId());
        return result;
    }

    @Transactional
    public LoginResponse createOrganization(UUID userId, CreateOrganizationRequest request) {
        User user =
                users.findByIdAndActiveTrue(userId).orElseThrow(() -> new UnauthorizedException("User unavailable"));
        Organization organization = new Organization();
        organization.setName(request.organizationName());
        organization.setEmail(user.getEmail());
        organization.setCountry("India");
        organization.setCurrency("INR");
        organization.setFinancialYearStart("04-01");
        organization.setInvoicePrefix("INV");
        organization.setInvoiceNumberFormat("{PREFIX}/{FY}/{SEQ:6}");
        organization.setOnboardingCompleted(false);
        organizations.save(organization);

        OrganizationSettings settingsEntity = new OrganizationSettings();
        settingsEntity.setOrganizationId(organization.getId());
        settings.save(settingsEntity);

        Role role = roles.findByCode("ORGANIZATION_ADMIN")
                .orElseThrow(() -> new BusinessException("Organization admin role is unavailable"));
        membershipService.createAdminMembership(user, organization, role);
        return tokens(user, organization.getId());
    }

    @Transactional
    public LoginResponse switchOrganization(UUID userId, SwitchOrganizationRequest request) {
        User user =
                users.findByIdAndActiveTrue(userId).orElseThrow(() -> new UnauthorizedException("User unavailable"));
        membershipService.requireActiveMembership(userId, request.organizationId());
        user.setLastActiveOrganizationId(request.organizationId());
        user.setOrganizationId(request.organizationId());
        users.save(user);
        return tokens(user, request.organizationId());
    }

    @Transactional
    public void logout(String token) {
        refreshTokens.findByTokenHashAndRevokedFalse(hash(token)).ifPresent(existing -> existing.setRevoked(true));
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        User user = users.findByOrganizationIdAndEmailIgnoreCase(request.organizationId(), request.email())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        String token = UUID.randomUUID().toString();
        user.setPasswordResetToken(hash(token));
        user.setPasswordResetExpiry(Instant.now().plus(Duration.ofHours(1)));
        log.info("Password reset link: {}/reset-password?token={}", frontendUrl, token);
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        User user = users.findByPasswordResetToken(hash(request.token()))
                .filter(existing -> existing.getPasswordResetExpiry().isAfter(Instant.now()))
                .orElseThrow(() -> new BusinessException("Invalid or expired reset token"));
        user.setPasswordHash(encoder.encode(request.newPassword()));
        user.setPasswordResetToken(null);
        user.setPasswordResetExpiry(null);
        refreshTokens.deleteByUserId(user.getId());
    }

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (!encoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException("Current password is incorrect");
        }
        user.setPasswordHash(encoder.encode(request.newPassword()));
        refreshTokens.deleteByUserId(userId);
    }

    @Transactional
    public LoginResponse registerOrganization(RegisterOrganizationRequest request) {
        Organization organization = new Organization();
        organization.setName(request.organizationName());
        organization.setEmail(request.email());
        organization.setCountry("India");
        organization.setCurrency("INR");
        organization.setFinancialYearStart("04-01");
        organization.setInvoicePrefix("INV");
        organization.setInvoiceNumberFormat("{PREFIX}/{FY}/{SEQ:6}");
        organization.setOnboardingCompleted(false);
        organizations.save(organization);

        OrganizationSettings settingsEntity = new OrganizationSettings();
        settingsEntity.setOrganizationId(organization.getId());
        settings.save(settingsEntity);

        Role role = roles.findByCode("ORGANIZATION_ADMIN")
                .orElseThrow(() -> new BusinessException("Organization admin role is unavailable"));

        User user = new User();
        user.setEmail(request.email().trim().toLowerCase());
        user.setPasswordHash(encoder.encode(request.password()));
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setPhone(request.phone());
        user.setOrganizationId(organization.getId());
        user.setLastActiveOrganizationId(organization.getId());
        user.getRoles().add(role);
        users.save(user);
        membershipService.createAdminMembership(user, organization, role);
        return tokens(user, organization.getId());
    }

    private LoginResponse tokens(User user, UUID organizationId) {
        UserPrincipal principal = details.load(user.getId(), organizationId);
        String access = jwt.createAccessToken(principal);
        String refresh = jwt.createRefreshToken(principal);
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUserId(user.getId());
        refreshToken.setTokenHash(hash(refresh));
        refreshToken.setExpiresAt(Instant.now().plus(Duration.ofDays(14)));
        refreshTokens.save(refreshToken);
        OrganizationAccessResponse activeOrganization =
                membershipService.listAccessibleOrganizations(user.getId()).stream()
                        .filter(org -> org.id().equals(organizationId))
                        .findFirst()
                        .orElseThrow(() -> new UnauthorizedException("Organization access denied"));
        return new LoginResponse(
                access,
                refresh,
                jwt.accessExpirySeconds(),
                response(user),
                activeOrganization,
                membershipService.listAccessibleOrganizations(user.getId()));
    }

    private UserResponse response(User user) {
        return new UserResponse(
                user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(), user.getUserStatus());
    }

    private String hash(String value) {
        try {
            return java.util.HexFormat.of()
                    .formatHex(java.security.MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
