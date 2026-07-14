package com.flowledger.notification;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

public class NotificationRequest {
    private NotificationType type = NotificationType.SYSTEM;
    private Set<NotificationChannel> channels = EnumSet.noneOf(NotificationChannel.class);
    private NotificationRecipient recipient;
    private String subject;
    private String body;
    private String htmlBody;
    private byte[] attachment;
    private String attachmentFilename;
    private UUID organizationId;
    private String relatedEntityType;
    private UUID relatedEntityId;

    public static NotificationRequest of(
            NotificationType type, NotificationRecipient recipient, String subject, String body) {
        NotificationRequest request = new NotificationRequest();
        request.type = type;
        request.recipient = recipient;
        request.subject = subject;
        request.body = body;
        return request;
    }

    public NotificationRequest channels(NotificationChannel... channels) {
        this.channels = EnumSet.noneOf(NotificationChannel.class);
        if (channels != null) {
            for (NotificationChannel channel : channels) {
                if (channel != null) this.channels.add(channel);
            }
        }
        return this;
    }

    public NotificationRequest channel(NotificationChannel channel) {
        return channels(channel);
    }

    public NotificationRequest html(String htmlBody) {
        this.htmlBody = htmlBody;
        return this;
    }

    public NotificationRequest attachment(String filename, byte[] bytes) {
        this.attachmentFilename = filename;
        this.attachment = bytes;
        return this;
    }

    public NotificationRequest organizationId(UUID organizationId) {
        this.organizationId = organizationId;
        return this;
    }

    public NotificationRequest related(String entityType, UUID entityId) {
        this.relatedEntityType = entityType;
        this.relatedEntityId = entityId;
        return this;
    }

    public NotificationType getType() {
        return type;
    }

    public Set<NotificationChannel> getChannels() {
        return channels;
    }

    public NotificationRecipient getRecipient() {
        return recipient;
    }

    public String getSubject() {
        return subject;
    }

    public String getBody() {
        return body;
    }

    public String getHtmlBody() {
        return htmlBody;
    }

    public byte[] getAttachment() {
        return attachment;
    }

    public String getAttachmentFilename() {
        return attachmentFilename;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getRelatedEntityType() {
        return relatedEntityType;
    }

    public UUID getRelatedEntityId() {
        return relatedEntityId;
    }
}
