package com.flowledger.notification;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "flowledger.notifications.email.enabled", havingValue = "true")
public class SmtpEmailNotificationService implements EmailNotificationService {
    private static final Logger log = LoggerFactory.getLogger(SmtpEmailNotificationService.class);

    private final JavaMailSender mailSender;

    public SmtpEmailNotificationService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void sendInvoice(String recipient, String subject, byte[] pdf) {
        send(recipient, subject, "Please find your invoice attached. (" + pdf.length + " bytes)");
    }

    @Override
    public void sendPaymentReminder(String recipient, String subject, String body) {
        send(recipient, subject, body);
    }

    @Override
    public void sendLeadFollowUp(String recipient, String subject, String body) {
        send(recipient, subject, body);
    }

    @Override
    public void sendMarketing(String recipient, String subject, String body) {
        send(recipient, subject, body);
    }

    @Override
    public void sendMarketingHtml(String recipient, String subject, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setTo(recipient);
            helper.setSubject(subject);
            helper.setText(html == null ? "" : html, true);
            mailSender.send(message);
            log.info("SMTP HTML email sent to {}: {}", recipient, subject);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to send HTML email to " + recipient, ex);
        }
    }

    private void send(String recipient, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(recipient);
        message.setSubject(subject);
        message.setText(body == null ? "" : body);
        mailSender.send(message);
        log.info("SMTP email sent to {}: {}", recipient, subject);
    }
}
