package com.flowledger.auth.service;

import com.flowledger.auth.dto.UserDtos.*;
import com.flowledger.auth.entity.Role;
import com.flowledger.auth.entity.User;
import com.flowledger.auth.repository.RoleRepository;
import com.flowledger.auth.repository.UserRepository;
import com.flowledger.common.exception.BusinessException;
import com.flowledger.common.exception.ConflictException;
import com.flowledger.common.exception.ResourceNotFoundException;
import com.flowledger.common.service.OrganizationScopedService;
import com.flowledger.organization.entity.Organization;
import com.flowledger.organization.repository.OrganizationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional
public class UserService extends OrganizationScopedService {
    private final UserRepository users;
    private final RoleRepository roles;
    private final OrganizationRepository organizations;
    private final PasswordEncoder encoder;

    @Value("${flowledger.app.frontend-url}")
    private String frontendUrl;

    public UserService(
            UserRepository users,
            RoleRepository roles,
            OrganizationRepository organizations,
            PasswordEncoder encoder
    ) {
        this.users = users;
        this.roles = roles;
        this.organizations = organizations;
        this.encoder = encoder;
    }

    @Transactional(readOnly = true)
    public List<UserListResponse> list() {
        return users.findByOrganizationId(orgId()).stream().map(this::toListResponse).toList();
    }

    @Transactional(readOnly = true)
    public UserListResponse get(UUID id) {
        return toListResponse(load(id));
    }

    public UserListResponse invite(InviteUserRequest request) {
        UUID org = orgId();
        if (users.existsByOrganizationIdAndEmailIgnoreCase(org, request.email())) {
            throw new ConflictException("A user with this email already exists in your organization");
        }
        Role role = roles.findByCode(request.role())
                .orElseThrow(() -> new BusinessException("Invalid role: " + request.role()));
        if ("SUPER_ADMIN".equals(role.getCode())) {
            throw new BusinessException("Cannot assign SUPER_ADMIN role");
        }
        String token = UUID.randomUUID().toString();
        User user = new User();
        user.setOrganizationId(org);
        user.setEmail(request.email().trim().toLowerCase());
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setPhone(request.phone());
        user.setPasswordHash(encoder.encode(UUID.randomUUID().toString()));
        user.setActive(false);
        user.setUserStatus("INVITED");
        user.setInvitationToken(hash(token));
        user.setInvitationExpiry(Instant.now().plus(Duration.ofDays(7)));
        user.getRoles().add(role);
        users.save(user);
        log.info("Invitation link: {}/accept-invite?token={}", frontendUrl, token);
        return toListResponse(user);
    }

    public UserListResponse update(UUID id, UpdateUserRequest request) {
        User user = load(id);
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setPhone(request.phone());
        return toListResponse(users.save(user));
    }

    public UserListResponse changeRole(UUID id, ChangeRoleRequest request) {
        User user = load(id);
        Role role = roles.findByCode(request.role())
                .orElseThrow(() -> new BusinessException("Invalid role: " + request.role()));
        if ("SUPER_ADMIN".equals(role.getCode())) {
            throw new BusinessException("Cannot assign SUPER_ADMIN role");
        }
        user.getRoles().clear();
        user.getRoles().add(role);
        return toListResponse(users.save(user));
    }

    public UserListResponse activate(UUID id) {
        User user = load(id);
        if ("INVITED".equals(user.getUserStatus())) {
            throw new BusinessException("Invited users must accept their invitation first");
        }
        user.setActive(true);
        user.setUserStatus("ACTIVE");
        return toListResponse(users.save(user));
    }

    public UserListResponse deactivate(UUID id) {
        User user = load(id);
        user.setActive(false);
        user.setUserStatus("INACTIVE");
        return toListResponse(users.save(user));
    }

    public UserListResponse resendInvitation(UUID id) {
        User user = load(id);
        if (!"INVITED".equals(user.getUserStatus())) {
            throw new BusinessException("User is not in invited status");
        }
        String token = UUID.randomUUID().toString();
        user.setInvitationToken(hash(token));
        user.setInvitationExpiry(Instant.now().plus(Duration.ofDays(7)));
        users.save(user);
        log.info("Invitation link: {}/accept-invite?token={}", frontendUrl, token);
        return toListResponse(user);
    }

    @Transactional(readOnly = true)
    public InvitationPreviewResponse previewInvitation(String token) {
        User user = users.findByInvitationToken(hash(token))
                .filter(u -> u.getInvitationExpiry() != null && u.getInvitationExpiry().isAfter(Instant.now()))
                .filter(u -> "INVITED".equals(u.getUserStatus()))
                .orElseThrow(() -> new BusinessException("Invalid or expired invitation"));
        Organization org = organizations.findById(user.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found"));
        return new InvitationPreviewResponse(org.getName(), user.getEmail(), user.getFirstName(), user.getLastName());
    }

    public void acceptInvitation(AcceptInvitationRequest request) {
        User user = users.findByInvitationToken(hash(request.token()))
                .filter(u -> u.getInvitationExpiry() != null && u.getInvitationExpiry().isAfter(Instant.now()))
                .filter(u -> "INVITED".equals(u.getUserStatus()))
                .orElseThrow(() -> new BusinessException("Invalid or expired invitation"));
        user.setPasswordHash(encoder.encode(request.password()));
        user.setActive(true);
        user.setEmailVerified(true);
        user.setUserStatus("ACTIVE");
        user.setInvitationToken(null);
        user.setInvitationExpiry(null);
        users.save(user);
    }

    private User load(UUID id) {
        return required(users.findByIdAndOrganizationId(id, orgId()), "User");
    }

    private UserListResponse toListResponse(User user) {
        Set<String> roleCodes = user.getRoles().stream().map(Role::getCode).collect(Collectors.toSet());
        return new UserListResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getPhone(),
                user.getUserStatus(),
                roleCodes,
                user.getLastLoginAt()
        );
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
