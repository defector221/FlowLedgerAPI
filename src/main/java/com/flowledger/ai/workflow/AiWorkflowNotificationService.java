package com.flowledger.ai.workflow;

import com.flowledger.ai.config.ConditionalOnAiEnabled;
import com.flowledger.auth.entity.OrganizationMembership;
import com.flowledger.auth.entity.Role;
import com.flowledger.auth.entity.User;
import com.flowledger.auth.repository.OrganizationMembershipRepository;
import com.flowledger.auth.repository.UserRepository;
import com.flowledger.notification.NotificationChannel;
import com.flowledger.notification.NotificationRecipient;
import com.flowledger.notification.NotificationRequest;
import com.flowledger.notification.NotificationService;
import com.flowledger.notification.NotificationType;
import com.flowledger.transport.entity.ApprovalRequest;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Sends in-app notifications for AI workflow approval actions. */
@Service
@ConditionalOnAiEnabled
public class AiWorkflowNotificationService {
    private static final Logger log = LoggerFactory.getLogger(AiWorkflowNotificationService.class);

    private final NotificationService notifications;
    private final OrganizationMembershipRepository memberships;
    private final UserRepository users;

    public AiWorkflowNotificationService(
            NotificationService notifications, OrganizationMembershipRepository memberships, UserRepository users) {
        this.notifications = notifications;
        this.memberships = memberships;
        this.users = users;
    }

    public void notifyApprovers(ApprovalRequest request, String role, String title, String body) {
        if (request == null) {
            return;
        }
        Set<UUID> recipients = resolveApproverUserIds(request.getOrganizationId(), role, request.getRequestedBy());
        for (UUID userId : recipients) {
            sendInApp(userId, request, title, body, true);
        }
    }

    /** Decision updates for the requester — deep-links to the source document when possible. */
    public void notifyDecision(ApprovalRequest request, String title, String body) {
        if (request == null || request.getRequestedBy() == null) {
            return;
        }
        sendInApp(request.getRequestedBy(), request, title, body, false);
    }

    public void notifyRequester(ApprovalRequest request, String title, String body) {
        notifyDecision(request, title, body);
    }

    private void sendInApp(
            UUID userId, ApprovalRequest request, String title, String body, boolean linkToWorkflowInbox) {
        try {
            User user = users.findById(userId).orElse(null);
            String relatedType = linkToWorkflowInbox ? "ApprovalRequest" : request.getEntityType();
            UUID relatedId = linkToWorkflowInbox ? request.getId() : request.getEntityId();
            notifications.sendAsync(NotificationRequest.of(
                            NotificationType.WORKFLOW_APPROVAL,
                            NotificationRecipient.user(
                                    userId, user == null ? null : user.getEmail(), displayName(user)),
                            title,
                            body)
                    .channel(NotificationChannel.IN_APP)
                    .organizationId(request.getOrganizationId())
                    .related(relatedType, relatedId));
        } catch (Exception ex) {
            log.warn("Failed to queue workflow notification for user {}: {}", userId, ex.getMessage());
        }
    }

    private Set<UUID> resolveApproverUserIds(UUID orgId, String requiredRole, UUID excludeUserId) {
        Set<UUID> ids = new HashSet<>();
        String role = requiredRole == null || requiredRole.isBlank()
                ? "ORGANIZATION_ADMIN"
                : requiredRole.trim().toUpperCase(Locale.ROOT);
        for (OrganizationMembership membership : memberships.findByOrganizationId(orgId)) {
            if (!"ACTIVE".equalsIgnoreCase(membership.getStatus())) {
                continue;
            }
            if (excludeUserId != null && excludeUserId.equals(membership.getUserId())) {
                continue;
            }
            Set<String> codes = new HashSet<>();
            for (Role membershipRole : membership.getRoles()) {
                if (membershipRole.getCode() != null) {
                    codes.add(membershipRole.getCode().toUpperCase(Locale.ROOT));
                }
            }
            if (codes.contains("ORGANIZATION_ADMIN") || codes.contains(role)) {
                ids.add(membership.getUserId());
            }
        }
        return ids;
    }

    private static String displayName(User user) {
        if (user == null) {
            return null;
        }
        String first = user.getFirstName() == null ? "" : user.getFirstName().trim();
        String last = user.getLastName() == null ? "" : user.getLastName().trim();
        String name = (first + " " + last).trim();
        return name.isBlank() ? user.getEmail() : name;
    }
}
