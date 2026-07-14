package com.flowledger.notification;

public interface EmailNotificationService {
    void sendInvoice(String recipient, String subject, byte[] pdf);

    void sendPaymentReminder(String recipient, String subject, String body);

    void sendLeadFollowUp(String recipient, String subject, String body);

    void sendMarketing(String recipient, String subject, String body);

    void sendMarketingHtml(String recipient, String subject, String html);
}
