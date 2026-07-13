package com.flowledger.payment.service;

import com.flowledger.common.tenant.TenantContext;
import com.flowledger.notification.EmailNotificationService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Slf4j
@Transactional
public class PaymentReminderService {
    @PersistenceContext
    private EntityManager em;

    private final EmailNotificationService emailNotifications;

    public PaymentReminderService(EmailNotificationService emailNotifications) {
        this.emailNotifications = emailNotifications;
    }

    public UUID sendNow(UUID invoiceId) {
        UUID orgId = TenantContext.getOrganizationId();
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                        "select si.id, si.invoice_number, si.outstanding_amount, c.email, c.customer_name "
                                + "from sales_invoices si "
                                + "left join customers c on c.id = si.customer_id "
                                + "where si.id = :id and si.organization_id = :org")
                .setParameter("id", invoiceId)
                .setParameter("org", orgId)
                .getResultList();
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found");
        }
        Object[] row = rows.get(0);
        String invoiceNumber = row[1] == null ? invoiceId.toString() : row[1].toString();
        String outstanding = row[2] == null ? "0" : row[2].toString();
        String email = row[3] == null ? null : row[3].toString();
        String customerName = row[4] == null ? "Customer" : row[4].toString();
        String subject = "Payment reminder for invoice " + invoiceNumber;
        String body = "Dear " + customerName + ", outstanding amount for invoice " + invoiceNumber + " is " + outstanding
                + ".";

        UUID reminderId = UUID.randomUUID();
        em.createNativeQuery(
                        "insert into payment_reminders "
                                + "(id, organization_id, invoice_id, channel, recipient, subject, body, status, scheduled_at, sent_at, created_at, updated_at) "
                                + "values (:id, :org, :invoice, 'EMAIL', :recipient, :subject, :body, :status, :now, :sentAt, :now, :now)")
                .setParameter("id", reminderId)
                .setParameter("org", orgId)
                .setParameter("invoice", invoiceId)
                .setParameter("recipient", email)
                .setParameter("subject", subject)
                .setParameter("body", body)
                .setParameter("status", email == null || email.isBlank() ? "FAILED" : "SENT")
                .setParameter("now", OffsetDateTime.now())
                .setParameter("sentAt", email == null || email.isBlank() ? null : OffsetDateTime.now())
                .executeUpdate();

        if (email != null && !email.isBlank()) {
            emailNotifications.sendPaymentReminder(email, subject, body);
        } else {
            log.warn("No customer email for invoice {}; reminder recorded as FAILED", invoiceId);
        }
        return reminderId;
    }

    @Scheduled(cron = "0 0 9 * * *")
    public void processDailyReminders() {
        log.info("Payment reminder daily job stub started");
        Number count = (Number) em.createNativeQuery(
                        "select count(*) from sales_invoices "
                                + "where outstanding_amount > 0 and due_date is not null and due_date <= CURRENT_DATE "
                                + "and status not in ('CANCELLED','DRAFT')")
                .getSingleResult();
        log.info("Payment reminder daily job stub found {} overdue invoices", count.longValue());
    }
}
