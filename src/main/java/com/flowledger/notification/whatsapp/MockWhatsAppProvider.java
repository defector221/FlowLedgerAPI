package com.flowledger.notification.whatsapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "flowledger.notifications.whatsapp.provider", havingValue = "mock", matchIfMissing = true)
public class MockWhatsAppProvider implements WhatsAppProvider {
    private static final Logger log = LoggerFactory.getLogger(MockWhatsAppProvider.class);

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public void sendText(String phoneNumber, String message) {
        log.info("Mock WhatsApp text to {}: {}", phoneNumber, message);
    }

    @Override
    public void sendDocument(String phoneNumber, String message, String filename, byte[] bytes) {
        log.info(
                "Mock WhatsApp document to {}: {} ({} / {} bytes)",
                phoneNumber,
                message,
                filename,
                bytes == null ? 0 : bytes.length);
    }
}
