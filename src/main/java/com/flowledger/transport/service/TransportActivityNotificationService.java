package com.flowledger.transport.service;

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
import com.flowledger.transport.entity.Shipment;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Fans out shipment timeline activity as in-app notifications for transport viewers. */
@Service
public class TransportActivityNotificationService {
    private static final Logger log = LoggerFactory.getLogger(TransportActivityNotificationService.class);
    private static final Set<String> SKIP = Set.of("GPS_UPDATED");

    private final NotificationService notifications;
    private final OrganizationMembershipRepository memberships;
    private final UserRepository users;

    public TransportActivityNotificationService(
            NotificationService notifications, OrganizationMembershipRepository memberships, UserRepository users) {
        this.notifications = notifications;
        this.memberships = memberships;
        this.users = users;
    }

    public void notifyShipmentEvent(Shipment shipment, String eventType, String remarks) {
        if (shipment == null || eventType == null || eventType.isBlank()) return;
        String type = eventType.trim().toUpperCase(Locale.ROOT);
        if (SKIP.contains(type)) return;

        String title = titleFor(type, shipment.getShipmentNumber());
        String body = bodyFor(shipment, type, remarks);
        NotificationType notificationType = mapType(type);

        for (UUID userId : resolveRecipients(shipment.getOrganizationId())) {
            try {
                User user = users.findById(userId).orElse(null);
                notifications.sendAsync(NotificationRequest.of(
                                notificationType,
                                NotificationRecipient.user(
                                        userId, user == null ? null : user.getEmail(), displayName(user)),
                                title,
                                body)
                        .channel(NotificationChannel.IN_APP)
                        .organizationId(shipment.getOrganizationId())
                        .related("Shipment", shipment.getId()));
            } catch (Exception ex) {
                log.warn("Failed to queue shipment notification for {}: {}", userId, ex.getMessage());
            }
        }
    }

    private Set<UUID> resolveRecipients(UUID orgId) {
        Set<UUID> ids = new HashSet<>();
        for (OrganizationMembership membership : memberships.findByOrganizationId(orgId)) {
            if (!"ACTIVE".equalsIgnoreCase(membership.getStatus())) continue;
            if (canViewTransport(membership)) {
                ids.add(membership.getUserId());
            }
        }
        return ids;
    }

    private static boolean canViewTransport(OrganizationMembership membership) {
        for (Role role : membership.getRoles()) {
            if (role.getCode() != null && "ORGANIZATION_ADMIN".equalsIgnoreCase(role.getCode())) {
                return true;
            }
            if (role.getPermissions() == null) continue;
            for (var permission : role.getPermissions()) {
                String code = permission.getCode();
                if (code == null) continue;
                if (code.equalsIgnoreCase("TRANSPORT_VIEW")
                        || code.equalsIgnoreCase("TRANSPORT_TRACK")
                        || code.equalsIgnoreCase("TRANSPORT_ADMIN")
                        || code.equalsIgnoreCase("TRANSPORT_DISPATCH")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static NotificationType mapType(String eventType) {
        return switch (eventType) {
            case "ASSIGNED" -> NotificationType.SHIPMENT_ASSIGNED;
            case "DISPATCHED", "PARTIALLY_DISPATCHED", "LEG_DISPATCHED" -> NotificationType.SHIPMENT_DISPATCHED;
            case "CHECKPOINT" -> NotificationType.SHIPMENT_CHECKPOINT;
            case "DELIVERED", "CLOSED", "LEG_COMPLETED" -> NotificationType.SHIPMENT_DELIVERED;
            default -> NotificationType.SHIPMENT_ACTIVITY;
        };
    }

    private static String titleFor(String eventType, String shipmentNumber) {
        String number = shipmentNumber == null ? "Shipment" : shipmentNumber;
        return switch (eventType) {
            case "CREATED" -> "Shipment created · " + number;
            case "UPDATED" -> "Shipment updated · " + number;
            case "SUBMITTED" -> "Shipment submitted · " + number;
            case "APPROVED" -> "Shipment approved · " + number;
            case "REJECTED" -> "Shipment rejected · " + number;
            case "APPROVAL_REQUESTED" -> "Shipment approval needed · " + number;
            case "APPROVAL_APPROVED" -> "Shipment approval granted · " + number;
            case "APPROVAL_REJECTED" -> "Shipment approval rejected · " + number;
            case "ASSIGNED" -> "Shipment assigned · " + number;
            case "DISPATCHED", "PARTIALLY_DISPATCHED" -> "Shipment dispatched · " + number;
            case "IN_TRANSIT" -> "Shipment in transit · " + number;
            case "CHECKPOINT" -> "Shipment checkpoint · " + number;
            case "DELIVERED" -> "Shipment delivered · " + number;
            case "CLOSED" -> "Shipment closed · " + number;
            case "CANCELLED" -> "Shipment cancelled · " + number;
            case "LEG_CREATED" -> "Shipment leg added · " + number;
            case "LEG_UPDATED" -> "Shipment leg updated · " + number;
            case "LEG_DISPATCHED" -> "Shipment leg dispatched · " + number;
            case "LEG_ARRIVED" -> "Shipment leg arrived · " + number;
            case "LEG_COMPLETED" -> "Shipment leg completed · " + number;
            case "LEG_CANCELLED" -> "Shipment leg removed · " + number;
            case "NOTE" -> "Shipment note · " + number;
            case "PROVIDER_UPDATE" -> "Carrier update · " + number;
            case "POD_UPLOADED", "LEG_DOCUMENT_UPLOADED" -> "Shipment document · " + number;
            default -> humanize(eventType) + " · " + number;
        };
    }

    private static String bodyFor(Shipment shipment, String eventType, String remarks) {
        StringBuilder body = new StringBuilder();
        body.append(shipment.getShipmentNumber() == null ? "A shipment" : shipment.getShipmentNumber());
        body.append(" · ").append(humanize(eventType).toLowerCase(Locale.ROOT));
        if (shipment.getStatus() != null) {
            body.append(" · status ").append(humanize(shipment.getStatus().name()));
        }
        if (remarks != null && !remarks.isBlank()) {
            body.append(". ").append(remarks.trim());
        }
        return body.toString();
    }

    private static String humanize(String value) {
        if (value == null || value.isBlank()) return "Update";
        String[] parts = value.toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    private static String displayName(User user) {
        if (user == null) return null;
        String first = user.getFirstName() == null ? "" : user.getFirstName().trim();
        String last = user.getLastName() == null ? "" : user.getLastName().trim();
        String name = (first + " " + last).trim();
        return name.isBlank() ? user.getEmail() : name;
    }
}
