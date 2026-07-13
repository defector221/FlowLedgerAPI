package com.flowledger.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MockWhatsAppNotificationService implements WhatsAppNotificationService {
    private static final Logger log = LoggerFactory.getLogger(MockWhatsAppNotificationService.class);

    @Override
    public void sendInvoice(String phoneNumber, String message, byte[] pdf) {
        log.info("Mock WhatsApp invoice delivery to {}: {} ({} bytes)", phoneNumber, message, pdf.length);
    }

    @Override
    public void sendPaymentReminder(String phoneNumber, String message) {
        log.info("Mock WhatsApp payment reminder to {}: {}", phoneNumber, message);
    }

    @Override
    public void sendLeadFollowUp(String phoneNumber, String message) {
        log.info("Mock WhatsApp lead follow-up to {}: {}", phoneNumber, message);
    }

    @Override
    public void sendMarketing(String phoneNumber, String message) {
        log.info("Mock WhatsApp marketing to {}: {}", phoneNumber, message);
    }
}
