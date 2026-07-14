package com.flowledger.notification.whatsapp;

/**
 * Pluggable WhatsApp delivery. Current implementations: mock.
 * Future: Meta Cloud API / Twilio behind the same contract.
 */
public interface WhatsAppProvider {
    String name();

    void sendText(String phoneNumber, String message);

    void sendDocument(String phoneNumber, String message, String filename, byte[] bytes);
}
