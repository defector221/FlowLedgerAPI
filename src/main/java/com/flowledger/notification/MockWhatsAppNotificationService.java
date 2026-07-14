package com.flowledger.notification;

import com.flowledger.notification.whatsapp.WhatsAppProvider;
import org.springframework.stereotype.Service;

/** Legacy WhatsApp adapter — delegates to the pluggable WhatsAppProvider. */
@Service
public class MockWhatsAppNotificationService implements WhatsAppNotificationService {
    private final WhatsAppProvider provider;

    public MockWhatsAppNotificationService(WhatsAppProvider provider) {
        this.provider = provider;
    }

    @Override
    public void sendInvoice(String phoneNumber, String message, byte[] pdf) {
        provider.sendDocument(phoneNumber, message, "invoice.pdf", pdf);
    }

    @Override
    public void sendPaymentReminder(String phoneNumber, String message) {
        provider.sendText(phoneNumber, message);
    }

    @Override
    public void sendLeadFollowUp(String phoneNumber, String message) {
        provider.sendText(phoneNumber, message);
    }

    @Override
    public void sendMarketing(String phoneNumber, String message) {
        provider.sendText(phoneNumber, message);
    }
}
