package com.flowledger.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "flowledger.notifications.email.enabled", havingValue = "false", matchIfMissing = true)
public class MockEmailNotificationService implements EmailNotificationService {
    private static final Logger log = LoggerFactory.getLogger(MockEmailNotificationService.class);

    @Override
    public void sendInvoice(String recipient, String subject, byte[] pdf) {
        log.info("Mock email invoice delivery to {}: {} ({} bytes)", recipient, subject, pdf.length);
    }

    @Override
    public void sendPaymentReminder(String recipient, String subject, String body) {
        log.info("Mock email payment reminder to {}: {} — {}", recipient, subject, body);
    }

    @Override
    public void sendLeadFollowUp(String recipient, String subject, String body) {
        log.info("Mock email lead follow-up to {}: {} — {}", recipient, subject, body);
    }

    @Override
    public void sendMarketing(String recipient, String subject, String body) {
        log.info("Mock email marketing to {}: {} — {}", recipient, subject, body);
    }
}
