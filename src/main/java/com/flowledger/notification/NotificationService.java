package com.flowledger.notification;

import com.flowledger.notification.config.NotificationProperties;
import com.flowledger.notification.entity.InAppNotification;
import com.flowledger.notification.entity.NotificationDelivery;
import com.flowledger.notification.repository.InAppNotificationRepository;
import com.flowledger.notification.repository.NotificationDeliveryRepository;
import com.flowledger.notification.whatsapp.WhatsAppProvider;
import com.flowledger.common.tenant.TenantContext;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class NotificationService {
    private final EmailNotificationService email;
    private final WhatsAppProvider whatsApp;
    private final InAppNotificationRepository inAppNotifications;
    private final NotificationDeliveryRepository deliveries;
    private final NotificationProperties properties;

    public NotificationService(
            EmailNotificationService email,
            WhatsAppProvider whatsApp,
            InAppNotificationRepository inAppNotifications,
            NotificationDeliveryRepository deliveries,
            NotificationProperties properties) {
        this.email = email;
        this.whatsApp = whatsApp;
        this.inAppNotifications = inAppNotifications;
        this.deliveries = deliveries;
        this.properties = properties;
    }

    @Async
    public void sendAsync(NotificationRequest request) {
        try {
            send(request);
        } catch (Exception ex) {
            log.warn("Async notification failed: {}", ex.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NotificationResult send(NotificationRequest request) {
        NotificationResult result = new NotificationResult();
        if (request == null || request.getRecipient() == null) {
            return result;
        }
        Set<NotificationChannel> channels = request.getChannels() == null || request.getChannels().isEmpty()
                ? EnumSet.of(NotificationChannel.EMAIL)
                : request.getChannels();

        UUID orgId = request.getOrganizationId() != null
                ? request.getOrganizationId()
                : TenantContext.organizationId().orElse(null);

        for (NotificationChannel channel : channels) {
            try {
                switch (channel) {
                    case EMAIL -> result.add(sendEmail(request, orgId));
                    case WHATSAPP -> result.add(sendWhatsApp(request, orgId));
                    case IN_APP -> result.add(sendInApp(request, orgId));
                    case SMS -> result.add(NotificationResult.ChannelResult.skipped(
                            channel, "SMS channel is not implemented"));
                }
            } catch (Exception ex) {
                log.warn("Notification channel {} failed: {}", channel, ex.getMessage());
                result.add(NotificationResult.ChannelResult.failed(
                        channel, recipientFor(channel, request.getRecipient()), ex.getMessage()));
                recordDelivery(request, orgId, channel, recipientFor(channel, request.getRecipient()), "FAILED", null, ex.getMessage());
            }
        }
        return result;
    }

    private NotificationResult.ChannelResult sendEmail(NotificationRequest request, UUID orgId) {
        NotificationRecipient recipient = request.getRecipient();
        String to = recipient.email();
        if (to == null || to.isBlank()) {
            NotificationResult.ChannelResult skipped =
                    NotificationResult.ChannelResult.skipped(NotificationChannel.EMAIL, "Missing email");
            recordDelivery(request, orgId, NotificationChannel.EMAIL, null, "SKIPPED", null, skipped.error());
            return skipped;
        }
        String subject = request.getSubject() == null ? "FlowLedger notification" : request.getSubject();
        if (request.getAttachment() != null && request.getAttachment().length > 0) {
            email.sendInvoice(to, subject, request.getAttachment());
        } else if (request.getHtmlBody() != null && !request.getHtmlBody().isBlank()) {
            email.sendMarketingHtml(to, subject, request.getHtmlBody());
        } else {
            email.sendPaymentReminder(to, subject, request.getBody() == null ? "" : request.getBody());
        }
        recordDelivery(request, orgId, NotificationChannel.EMAIL, to, "SENT", null, null);
        return NotificationResult.ChannelResult.ok(NotificationChannel.EMAIL, to);
    }

    private NotificationResult.ChannelResult sendWhatsApp(NotificationRequest request, UUID orgId) {
        if (!properties.getWhatsapp().isEnabled()) {
            NotificationResult.ChannelResult skipped = NotificationResult.ChannelResult.skipped(
                    NotificationChannel.WHATSAPP, "WhatsApp notifications disabled");
            recordDelivery(
                    request,
                    orgId,
                    NotificationChannel.WHATSAPP,
                    request.getRecipient().phone(),
                    "SKIPPED",
                    whatsApp.name(),
                    skipped.error());
            return skipped;
        }
        String phone = request.getRecipient().phone();
        if (phone == null || phone.isBlank()) {
            NotificationResult.ChannelResult skipped =
                    NotificationResult.ChannelResult.skipped(NotificationChannel.WHATSAPP, "Missing phone");
            recordDelivery(
                    request, orgId, NotificationChannel.WHATSAPP, null, "SKIPPED", whatsApp.name(), skipped.error());
            return skipped;
        }
        String message = firstNonBlank(request.getBody(), request.getSubject(), "FlowLedger notification");
        if (request.getAttachment() != null && request.getAttachment().length > 0) {
            whatsApp.sendDocument(
                    phone,
                    message,
                    request.getAttachmentFilename() == null ? "document.pdf" : request.getAttachmentFilename(),
                    request.getAttachment());
        } else {
            whatsApp.sendText(phone, message);
        }
        recordDelivery(request, orgId, NotificationChannel.WHATSAPP, phone, "SENT", whatsApp.name(), null);
        return NotificationResult.ChannelResult.ok(NotificationChannel.WHATSAPP, phone);
    }

    private NotificationResult.ChannelResult sendInApp(NotificationRequest request, UUID orgId) {
        UUID userId = request.getRecipient().userId();
        if (userId == null) {
            NotificationResult.ChannelResult skipped =
                    NotificationResult.ChannelResult.skipped(NotificationChannel.IN_APP, "Missing userId");
            recordDelivery(request, orgId, NotificationChannel.IN_APP, null, "SKIPPED", null, skipped.error());
            return skipped;
        }
        InAppNotification notification = new InAppNotification();
        notification.setOrganizationId(orgId);
        notification.setUserId(userId);
        notification.setTitle(firstNonBlank(request.getSubject(), request.getType().name()));
        notification.setBody(request.getBody());
        notification.setNotificationType(request.getType().name());
        notification.setEntityType(request.getRelatedEntityType());
        notification.setEntityId(request.getRelatedEntityId());
        notification.setCreatedAt(OffsetDateTime.now());
        inAppNotifications.save(notification);
        recordDelivery(request, orgId, NotificationChannel.IN_APP, userId.toString(), "SENT", "in-app", null);
        return NotificationResult.ChannelResult.ok(NotificationChannel.IN_APP, userId.toString());
    }

    private void recordDelivery(
            NotificationRequest request,
            UUID orgId,
            NotificationChannel channel,
            String recipient,
            String status,
            String providerRef,
            String error) {
        NotificationDelivery delivery = new NotificationDelivery();
        delivery.setOrganizationId(orgId);
        delivery.setNotificationType(request.getType() == null ? NotificationType.SYSTEM.name() : request.getType().name());
        delivery.setChannel(channel.name());
        delivery.setRecipient(truncate(recipient, 255));
        delivery.setSubject(truncate(request.getSubject(), 255));
        delivery.setStatus(status);
        delivery.setProviderRef(truncate(providerRef, 255));
        delivery.setErrorMessage(truncate(error, 2000));
        delivery.setRelatedEntityType(request.getRelatedEntityType());
        delivery.setRelatedEntityId(request.getRelatedEntityId());
        delivery.setCreatedAt(OffsetDateTime.now());
        deliveries.save(delivery);
    }

    private static String recipientFor(NotificationChannel channel, NotificationRecipient recipient) {
        if (recipient == null) return null;
        return switch (channel) {
            case EMAIL -> recipient.email();
            case WHATSAPP, SMS -> recipient.phone();
            case IN_APP -> recipient.userId() == null ? null : recipient.userId().toString();
        };
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return "";
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
