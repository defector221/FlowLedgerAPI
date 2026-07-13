package com.flowledger.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

interface EmailNotificationService {
    void sendInvoice(String recipient, String subject, byte[] pdf);
}

interface WhatsAppNotificationService {
    void sendInvoice(String phoneNumber, String message, byte[] pdf);
}

@Service
class MockEmailNotificationService implements EmailNotificationService {
    private static final Logger log = LoggerFactory.getLogger(MockEmailNotificationService.class);
    public void sendInvoice(String recipient, String subject, byte[] pdf) {
        log.info("Mock email invoice delivery to {}: {} ({} bytes)", recipient, subject, pdf.length);
    }
}

@Service
class MockWhatsAppNotificationService implements WhatsAppNotificationService {
    private static final Logger log = LoggerFactory.getLogger(MockWhatsAppNotificationService.class);
    public void sendInvoice(String phoneNumber, String message, byte[] pdf) {
        log.info("Mock WhatsApp invoice delivery to {}: {} ({} bytes)", phoneNumber, message, pdf.length);
    }
}
