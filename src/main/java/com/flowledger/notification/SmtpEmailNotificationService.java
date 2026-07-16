package com.flowledger.notification;

import com.flowledger.notification.config.NotificationProperties;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "flowledger.notifications.email.enabled", havingValue = "true")
public class SmtpEmailNotificationService implements EmailNotificationService {
    private static final Logger log = LoggerFactory.getLogger(SmtpEmailNotificationService.class);

    private final JavaMailSender mailSender;
    private final NotificationProperties properties;

    public SmtpEmailNotificationService(JavaMailSender mailSender, NotificationProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void sendInvoice(String recipient, String subject, byte[] pdf) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            applyFrom(helper);
            helper.setTo(recipient);
            helper.setSubject(subject);
            helper.setText("Please find your invoice attached.", false);
            helper.addAttachment("invoice.pdf", new ByteArrayResource(pdf == null ? new byte[0] : pdf));
            mailSender.send(message);
            log.info("SMTP invoice email sent to {}: {}", recipient, subject);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to send invoice email to " + recipient, ex);
        }
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
            applyFrom(helper);
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
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            applyFrom(helper);
            helper.setTo(recipient);
            helper.setSubject(subject);
            helper.setText(body == null ? "" : body, false);
            mailSender.send(message);
            log.info("SMTP email sent to {}: {}", recipient, subject);
        } catch (Exception ex) {
            // Fall back to simple mail if Mime fails unexpectedly
            SimpleMailMessage simple = new SimpleMailMessage();
            simple.setTo(recipient);
            simple.setSubject(subject);
            simple.setText(body == null ? "" : body);
            String from = properties.getEmail().getFromEmail();
            if (from != null && !from.isBlank()) simple.setFrom(from);
            mailSender.send(simple);
            log.info("SMTP simple email sent to {}: {}", recipient, subject);
        }
    }

    private void applyFrom(MimeMessageHelper helper) throws Exception {
        String from = properties.getEmail().getFromEmail();
        String name = properties.getEmail().getFromName();
        if (from != null && !from.isBlank()) {
            if (name != null && !name.isBlank()) helper.setFrom(from, name);
            else helper.setFrom(from);
        }
    }
}
