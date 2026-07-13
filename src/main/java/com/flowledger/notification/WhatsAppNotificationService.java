package com.flowledger.notification;

public interface WhatsAppNotificationService {
    void sendInvoice(String phoneNumber, String message, byte[] pdf);

    void sendPaymentReminder(String phoneNumber, String message);

    void sendLeadFollowUp(String phoneNumber, String message);

    void sendMarketing(String phoneNumber, String message);
}
