package com.flowledger.auth.service;

import com.flowledger.auth.dto.OrganizationAccessResponse;
import com.flowledger.auth.entity.OrganizationMembership;
import com.flowledger.auth.entity.Role;
import com.flowledger.auth.entity.User;
import com.flowledger.auth.repository.OrganizationMembershipRepository;
import com.flowledger.auth.repository.RoleRepository;
import com.flowledger.auth.repository.UserRepository;
import com.flowledger.common.exception.BusinessException;
import com.flowledger.common.exception.ConflictException;
import com.flowledger.common.exception.UnauthorizedException;
import com.flowledger.notification.NotificationChannel;
import com.flowledger.notification.NotificationRecipient;
import com.flowledger.notification.NotificationRequest;
import com.flowledger.notification.NotificationService;
import com.flowledger.notification.NotificationType;
import com.flowledger.organization.entity.Organization;
import com.flowledger.organization.repository.OrganizationRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@Transactional
public class OrganizationMembershipService {
    private final OrganizationMembershipRepository memberships;
    private final UserRepository users;
    private final RoleRepository roles;
    private final OrganizationRepository organizations;
    private final NotificationService notifications;

    @Value("${flowledger.app.frontend-url}")
    private String frontendUrl;

    public OrganizationMembershipService(
            OrganizationMembershipRepository memberships,
            UserRepository users,
            RoleRepository roles,
            OrganizationRepository organizations,
            NotificationService notifications) {
        this.memberships = memberships;
        this.users = users;
        this.roles = roles;
        this.organizations = organizations;
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public List<OrganizationAccessResponse> listAccessibleOrganizations(UUID userId) {
        return memberships.findByUserId(userId).stream()
                .filter(this::isLoginEligible)
                .map(this::toAccessResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrganizationAccessResponse resolveActiveOrganization(User user) {
        List<OrganizationMembership> eligible = memberships.findByUserId(user.getId()).stream()
                .filter(this::isLoginEligible)
                .toList();
        if (eligible.isEmpty()) {
            throw new UnauthorizedException("No active organization access");
        }
        OrganizationMembership selected = eligible.stream()
                .filter(m -> m.getOrganizationId().equals(user.getLastActiveOrganizationId()))
                .findFirst()
                .orElseGet(() -> eligible.stream()
                        .sorted(Comparator.comparing(
                                OrganizationMembership::getLastActiveAt,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                        .findFirst()
                        .orElse(eligible.get(0)));
        return toAccessResponse(selected);
    }

    public OrganizationMembership requireActiveMembership(UUID userId, UUID organizationId) {
        OrganizationMembership membership = memberships
                .findByUserIdAndOrganizationId(userId, organizationId)
                .orElseThrow(() -> new UnauthorizedException("Organization access denied"));
        if (!"ACTIVE".equals(membership.getStatus())) {
            throw new UnauthorizedException("Organization access is not active");
        }
        membership.setLastActiveAt(Instant.now());
        users.findById(userId).ifPresent(user -> user.setLastActiveOrganizationId(organizationId));
        return membership;
    }

    public OrganizationMembership createAdminMembership(User user, Organization organization, Role role) {
        OrganizationMembership membership = new OrganizationMembership();
        membership.setUserId(user.getId());
        membership.setOrganizationId(organization.getId());
        membership.setStatus("ACTIVE");
        membership.setLastActiveAt(Instant.now());
        membership.getRoles().add(role);
        user.setLastActiveOrganizationId(organization.getId());
        user.setOrganizationId(organization.getId());
        return memberships.save(membership);
    }

    public OrganizationMembership inviteToOrganization(
            UUID organizationId, User user, Role role, boolean existingAccount) {
        if (memberships.existsByOrganizationIdAndUserId(organizationId, user.getId())) {
            OrganizationMembership existing = memberships
                    .findByUserIdAndOrganizationId(user.getId(), organizationId)
                    .orElseThrow();
            if ("ACTIVE".equals(existing.getStatus()) || "INVITED".equals(existing.getStatus())) {
                throw new ConflictException("User already has access to this organization");
            }
            if ("INACTIVE".equals(existing.getStatus())) {
                existing.setStatus(existingAccount ? "ACTIVE" : "INVITED");
                existing.getRoles().clear();
                existing.getRoles().add(role);
                if (!existingAccount) {
                    issueInvitationToken(existing);
                }
                return memberships.save(existing);
            }
        }
        OrganizationMembership membership = new OrganizationMembership();
        membership.setUserId(user.getId());
        membership.setOrganizationId(organizationId);
        membership.setStatus(existingAccount ? "ACTIVE" : "INVITED");
        membership.getRoles().add(role);
        if (!existingAccount) {
            issueInvitationToken(membership);
        } else {
            log.info("Existing user {} added to organization {}", user.getEmail(), organizationId);
        }
        return memberships.save(membership);
    }

    public void resendInvitation(OrganizationMembership membership) {
        if (!"INVITED".equals(membership.getStatus())) {
            throw new BusinessException("User is not in invited status");
        }
        issueInvitationToken(membership);
        memberships.save(membership);
    }

    private void issueInvitationToken(OrganizationMembership membership) {
        String token = UUID.randomUUID().toString();
        membership.setInvitationToken(hash(token));
        membership.setInvitationExpiry(Instant.now().plus(Duration.ofDays(7)));
        User user = users.findById(membership.getUserId()).orElse(null);
        String orgName = organizations
                .findById(membership.getOrganizationId())
                .map(Organization::getName)
                .orElse("your organization");
        if (user != null && user.getEmail() != null && !user.getEmail().isBlank()) {
            String link = frontendUrl + "/accept-invite?token=" + token;
            String subject = "You're invited to " + orgName + " on FlowLedger";
            String body = "Hi "
                    + (user.getFirstName() == null ? "there" : user.getFirstName())
                    + ",\n\nYou've been invited to join "
                    + orgName
                    + " on FlowLedger.\nAccept your invitation:\n"
                    + link
                    + "\n\nThis link expires in 7 days.";
            notifications.send(NotificationRequest.of(
                            NotificationType.USER_INVITATION,
                            NotificationRecipient.email(user.getEmail(), user.getFirstName()),
                            subject,
                            body)
                    .channel(NotificationChannel.EMAIL)
                    .organizationId(membership.getOrganizationId())
                    .related("OrganizationMembership", membership.getId()));
        }
        log.info("Invitation email queued for membership {}", membership.getId());
    }

    @Transactional(readOnly = true)
    public List<OrganizationMembership> listOrganizationMemberships(UUID organizationId) {
        return memberships.findByOrganizationId(organizationId);
    }

    @Transactional(readOnly = true)
    public OrganizationMembership previewInvitationMembership(String token) {
        return memberships
                .findByInvitationToken(hash(token))
                .filter(m -> m.getInvitationExpiry() != null
                        && m.getInvitationExpiry().isAfter(Instant.now()))
                .filter(m -> "INVITED".equals(m.getStatus()))
                .orElseThrow(() -> new BusinessException("Invalid or expired invitation"));
    }

    public void acceptInvitationMembership(OrganizationMembership membership, User user, String passwordHash) {
        user.setPasswordHash(passwordHash);
        user.setActive(true);
        user.setEmailVerified(true);
        user.setUserStatus("ACTIVE");
        user.setInvitationToken(null);
        user.setInvitationExpiry(null);
        membership.setStatus("ACTIVE");
        membership.setInvitationToken(null);
        membership.setInvitationExpiry(null);
        membership.setLastActiveAt(Instant.now());
        user.setLastActiveOrganizationId(membership.getOrganizationId());
        user.setOrganizationId(membership.getOrganizationId());
        users.save(user);
        memberships.save(membership);
    }

    public Set<String> roleCodes(OrganizationMembership membership) {
        return membership.getRoles().stream().map(Role::getCode).collect(Collectors.toSet());
    }

    private boolean isLoginEligible(OrganizationMembership membership) {
        return "ACTIVE".equals(membership.getStatus());
    }

    private OrganizationAccessResponse toAccessResponse(OrganizationMembership membership) {
        Organization organization =
                organizations.findById(membership.getOrganizationId()).orElseThrow();
        return new OrganizationAccessResponse(
                organization.getId(),
                organization.getName(),
                roleCodes(membership),
                membership.getStatus(),
                organization.isOnboardingCompleted());
    }

    private String hash(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
