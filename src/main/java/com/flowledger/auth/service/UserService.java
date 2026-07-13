package com.flowledger.auth.service;

import com.flowledger.auth.dto.UserDtos.*;
import com.flowledger.auth.entity.OrganizationMembership;
import com.flowledger.auth.entity.Role;
import com.flowledger.auth.entity.User;
import com.flowledger.auth.repository.OrganizationMembershipRepository;
import com.flowledger.auth.repository.RoleRepository;
import com.flowledger.auth.repository.UserRepository;
import com.flowledger.common.exception.BusinessException;
import com.flowledger.common.exception.ResourceNotFoundException;
import com.flowledger.common.service.OrganizationScopedService;
import com.flowledger.organization.entity.Organization;
import com.flowledger.organization.repository.OrganizationRepository;
import com.flowledger.subscription.service.SubscriptionService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@Transactional
public class UserService extends OrganizationScopedService {
    private final UserRepository users;
    private final RoleRepository roles;
    private final OrganizationRepository organizations;
    private final OrganizationMembershipRepository memberships;
    private final OrganizationMembershipService membershipService;
    private final PasswordEncoder encoder;
    private final SubscriptionService subscriptions;

    public UserService(
            UserRepository users,
            RoleRepository roles,
            OrganizationRepository organizations,
            OrganizationMembershipRepository memberships,
            OrganizationMembershipService membershipService,
            PasswordEncoder encoder,
            SubscriptionService subscriptions) {
        this.users = users;
        this.roles = roles;
        this.organizations = organizations;
        this.memberships = memberships;
        this.membershipService = membershipService;
        this.encoder = encoder;
        this.subscriptions = subscriptions;
    }

    @Transactional(readOnly = true)
    public List<UserListResponse> list() {
        return memberships.findByOrganizationId(orgId()).stream()
                .map(this::toListResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserListResponse get(UUID membershipUserId) {
        return toListResponse(loadMembershipByUser(membershipUserId));
    }

    public UserListResponse invite(InviteUserRequest request) {
        UUID organizationId = orgId();
        subscriptions.checkCanInvite(organizationId);
        Role role = requireRole(request.role());
        String email = request.email().trim().toLowerCase();
        User user = users.findByEmailIgnoreCase(email).orElse(null);
        boolean existingAccount = user != null;
        if (!existingAccount) {
            user = new User();
            user.setEmail(email);
            user.setFirstName(request.firstName());
            user.setLastName(request.lastName());
            user.setPhone(request.phone());
            user.setPasswordHash(encoder.encode(UUID.randomUUID().toString()));
            user.setActive(false);
            user.setUserStatus("INVITED");
            users.save(user);
        }
        membershipService.inviteToOrganization(organizationId, user, role, existingAccount);
        return toListResponse(memberships
                .findByUserIdAndOrganizationId(user.getId(), organizationId)
                .orElseThrow());
    }

    public UserListResponse update(UUID userId, UpdateUserRequest request) {
        OrganizationMembership membership = loadMembershipByUser(userId);
        User user = users.findById(membership.getUserId()).orElseThrow();
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setPhone(request.phone());
        users.save(user);
        return toListResponse(membership);
    }

    public UserListResponse changeRole(UUID userId, ChangeRoleRequest request) {
        OrganizationMembership membership = loadMembershipByUser(userId);
        Role role = requireRole(request.role());
        membership.getRoles().clear();
        membership.getRoles().add(role);
        memberships.save(membership);
        return toListResponse(membership);
    }

    public UserListResponse activate(UUID userId) {
        OrganizationMembership membership = loadMembershipByUser(userId);
        if ("INVITED".equals(membership.getStatus())) {
            throw new BusinessException("Invited users must accept their invitation first");
        }
        membership.setStatus("ACTIVE");
        return toListResponse(memberships.save(membership));
    }

    public UserListResponse deactivate(UUID userId) {
        OrganizationMembership membership = loadMembershipByUser(userId);
        membership.setStatus("INACTIVE");
        return toListResponse(memberships.save(membership));
    }

    public UserListResponse resendInvitation(UUID userId) {
        OrganizationMembership membership = loadMembershipByUser(userId);
        membershipService.resendInvitation(membership);
        return toListResponse(membership);
    }

    @Transactional(readOnly = true)
    public InvitationPreviewResponse previewInvitation(String token) {
        OrganizationMembership membership = membershipService.previewInvitationMembership(token);
        Organization organization = organizations
                .findById(membership.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found"));
        User user = users.findById(membership.getUserId()).orElseThrow();
        return new InvitationPreviewResponse(
                organization.getName(), user.getEmail(), user.getFirstName(), user.getLastName());
    }

    public void acceptInvitation(AcceptInvitationRequest request) {
        OrganizationMembership membership = membershipService.previewInvitationMembership(request.token());
        User user = users.findById(membership.getUserId()).orElseThrow();
        membershipService.acceptInvitationMembership(membership, user, encoder.encode(request.password()));
    }

    private OrganizationMembership loadMembershipByUser(UUID userId) {
        return required(memberships.findByUserIdAndOrganizationId(userId, orgId()), "User");
    }

    private Role requireRole(String roleCode) {
        Role role = roles.findByCode(roleCode).orElseThrow(() -> new BusinessException("Invalid role: " + roleCode));
        if ("SUPER_ADMIN".equals(role.getCode())) {
            throw new BusinessException("Cannot assign SUPER_ADMIN role");
        }
        return role;
    }

    private UserListResponse toListResponse(OrganizationMembership membership) {
        User user = users.findById(membership.getUserId()).orElseThrow();
        Set<String> roleCodes =
                membership.getRoles().stream().map(Role::getCode).collect(Collectors.toSet());
        return new UserListResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getPhone(),
                membership.getStatus(),
                roleCodes,
                user.getLastLoginAt());
    }
}
