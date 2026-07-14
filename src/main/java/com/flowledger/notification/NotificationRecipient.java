package com.flowledger.notification;

import java.util.UUID;

/** Who should receive the notification. External parties use email/phone; org users also use userId for IN_APP. */
public record NotificationRecipient(UUID userId, String email, String phone, String displayName) {
    public static NotificationRecipient email(String email, String displayName) {
        return new NotificationRecipient(null, email, null, displayName);
    }

    public static NotificationRecipient phone(String phone, String displayName) {
        return new NotificationRecipient(null, null, phone, displayName);
    }

    public static NotificationRecipient user(UUID userId, String email, String displayName) {
        return new NotificationRecipient(userId, email, null, displayName);
    }
}
