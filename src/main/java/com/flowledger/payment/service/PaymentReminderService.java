package com.flowledger.payment.service;

import com.flowledger.common.exception.BusinessException;
import com.flowledger.common.exception.ResourceNotFoundException;
import com.flowledger.common.tenant.TenantContext;
import com.flowledger.notification.NotificationChannel;
import com.flowledger.notification.NotificationRecipient;
import com.flowledger.notification.NotificationRequest;
import com.flowledger.notification.NotificationResult;
import com.flowledger.notification.NotificationService;
import com.flowledger.notification.NotificationType;
import com.flowledger.organization.repository.OrganizationRepository;
import com.flowledger.payment.dto.PaymentReminderDtos.ReminderRuleRequest;
import com.flowledger.payment.dto.PaymentReminderDtos.ReminderRuleResponse;
import com.flowledger.payment.dto.PaymentReminderDtos.SendReminderRequest;
import com.flowledger.payment.dto.PaymentReminderDtos.SendReminderResponse;
import com.flowledger.payment.entity.PaymentReminderRule;
import com.flowledger.payment.repository.PaymentReminderRuleRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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

    private final NotificationService notifications;
    private final PaymentReminderRuleRepository rules;
    private final OrganizationRepository organizations;

    public PaymentReminderService(
            NotificationService notifications,
            PaymentReminderRuleRepository rules,
            OrganizationRepository organizations) {
        this.notifications = notifications;
        this.rules = rules;
        this.organizations = organizations;
    }

    public SendReminderResponse sendNow(UUID invoiceId, SendReminderRequest request) {
        InvoiceSnapshot invoice = loadInvoice(invoiceId);
        Set<NotificationChannel> channels = resolveChannels(request == null ? null : request.channels());
        return dispatchReminder(invoice, channels, null, null, null);
    }

    public List<ReminderRuleResponse> listRules() {
        return rules.findByOrganizationIdOrderByDaysOffsetAsc(TenantContext.getOrganizationId()).stream()
                .map(this::toRuleResponse)
                .toList();
    }

    public ReminderRuleResponse createRule(ReminderRuleRequest request) {
        PaymentReminderRule rule = new PaymentReminderRule();
        rule.setOrganizationId(TenantContext.getOrganizationId());
        applyRule(rule, request);
        return toRuleResponse(rules.save(rule));
    }

    public ReminderRuleResponse updateRule(UUID id, ReminderRuleRequest request) {
        PaymentReminderRule rule = rules.findByIdAndOrganizationId(id, TenantContext.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Reminder rule not found"));
        applyRule(rule, request);
        return toRuleResponse(rules.save(rule));
    }

    public void deleteRule(UUID id) {
        PaymentReminderRule rule = rules.findByIdAndOrganizationId(id, TenantContext.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Reminder rule not found"));
        rules.delete(rule);
    }

    @Scheduled(cron = "0 0 9 * * *")
    public void processDailyReminders() {
        log.info("Payment reminder daily job started");
        List<UUID> orgIds = organizations.findAll().stream().map(org -> org.getId()).toList();
        int sent = 0;
        for (UUID orgId : orgIds) {
            try {
                TenantContext.set(orgId, null);
                sent += processOrgRules(orgId);
            } catch (Exception ex) {
                log.warn("Payment reminder job failed for org {}: {}", orgId, ex.getMessage());
            } finally {
                TenantContext.clear();
            }
        }
        log.info("Payment reminder daily job finished; dispatched {}", sent);
    }

    private int processOrgRules(UUID orgId) {
        List<PaymentReminderRule> active = rules.findByOrganizationIdAndEnabledTrue(orgId);
        int count = 0;
        LocalDate today = LocalDate.now();
        for (PaymentReminderRule rule : active) {
            LocalDate targetDue = switch (rule.getOffsetType() == null ? "AFTER_DUE" : rule.getOffsetType()) {
                case "BEFORE_DUE" -> today.plusDays(rule.getDaysOffset());
                case "ON_DUE" -> today;
                default -> today.minusDays(rule.getDaysOffset());
            };
            List<InvoiceSnapshot> invoices = findDueInvoices(orgId, targetDue);
            NotificationChannel channel = parseChannel(rule.getChannel());
            for (InvoiceSnapshot invoice : invoices) {
                if (alreadySentToday(orgId, invoice.id(), rule.getId(), channel)) {
                    continue;
                }
                try {
                    dispatchReminder(
                            invoice,
                            EnumSet.of(channel),
                            rule.getId(),
                            rule.getSubjectTemplate(),
                            rule.getBodyTemplate());
                    count++;
                } catch (Exception ex) {
                    log.warn(
                            "Auto reminder failed for invoice {} / rule {}: {}",
                            invoice.id(),
                            rule.getId(),
                            ex.getMessage());
                }
            }
        }
        return count;
    }

    private SendReminderResponse dispatchReminder(
            InvoiceSnapshot invoice,
            Set<NotificationChannel> channels,
            UUID ruleId,
            String subjectTemplate,
            String bodyTemplate) {
        if (channels.isEmpty()) {
            throw new BusinessException("Select at least one reminder channel");
        }
        String subject = applyTemplate(
                subjectTemplate,
                "Payment reminder for invoice " + invoice.invoiceNumber(),
                invoice);
        String body = applyTemplate(
                bodyTemplate,
                "Dear " + invoice.customerName() + ", outstanding amount for invoice " + invoice.invoiceNumber()
                        + " is " + invoice.outstanding() + ".",
                invoice);

        NotificationRecipient recipient = new NotificationRecipient(
                null, invoice.email(), invoice.phone(), invoice.customerName());

        NotificationRequest notification = NotificationRequest.of(
                        NotificationType.PAYMENT_REMINDER, recipient, subject, body)
                .organizationId(TenantContext.organizationId().orElse(invoice.organizationId()))
                .related("SalesInvoice", invoice.id());
        notification.channels(channels.toArray(NotificationChannel[]::new));

        NotificationResult result = notifications.send(notification);
        boolean anySent = result.anySent();
        String status = anySent ? "SENT" : "FAILED";
        String error = result.getChannels().stream()
                .filter(c -> !c.sent() && c.error() != null)
                .map(NotificationResult.ChannelResult::error)
                .findFirst()
                .orElse(anySent ? null : "No channel delivered successfully");

        UUID reminderId = UUID.randomUUID();
        String primaryChannel = channels.iterator().next().name();
        String primaryRecipient = channels.contains(NotificationChannel.EMAIL) && invoice.email() != null
                ? invoice.email()
                : invoice.phone();

        em.createNativeQuery(
                        """
                        insert into payment_reminders
                        (id, organization_id, invoice_id, rule_id, channel, recipient, subject, body, status,
                         scheduled_at, sent_at, error_message, created_at, updated_at)
                        values (:id, :org, :invoice, :rule, :channel, :recipient, :subject, :body, :status,
                                :now, :sentAt, :error, :now, :now)
                        """)
                .setParameter("id", reminderId)
                .setParameter("org", invoice.organizationId())
                .setParameter("invoice", invoice.id())
                .setParameter("rule", ruleId)
                .setParameter("channel", primaryChannel)
                .setParameter("recipient", primaryRecipient)
                .setParameter("subject", subject)
                .setParameter("body", body)
                .setParameter("status", status)
                .setParameter("now", OffsetDateTime.now())
                .setParameter("sentAt", anySent ? OffsetDateTime.now() : null)
                .setParameter("error", error)
                .executeUpdate();

        // Also persist additional channels when multiple were requested
        for (NotificationResult.ChannelResult channelResult : result.getChannels()) {
            if (channelResult.channel().name().equals(primaryChannel)) continue;
            em.createNativeQuery(
                            """
                            insert into payment_reminders
                            (id, organization_id, invoice_id, rule_id, channel, recipient, subject, body, status,
                             scheduled_at, sent_at, error_message, created_at, updated_at)
                            values (:id, :org, :invoice, :rule, :channel, :recipient, :subject, :body, :status,
                                    :now, :sentAt, :error, :now, :now)
                            """)
                    .setParameter("id", UUID.randomUUID())
                    .setParameter("org", invoice.organizationId())
                    .setParameter("invoice", invoice.id())
                    .setParameter("rule", ruleId)
                    .setParameter("channel", channelResult.channel().name())
                    .setParameter("recipient", channelResult.recipient())
                    .setParameter("subject", subject)
                    .setParameter("body", body)
                    .setParameter("status", channelResult.sent() ? "SENT" : "FAILED")
                    .setParameter("now", OffsetDateTime.now())
                    .setParameter("sentAt", channelResult.sent() ? OffsetDateTime.now() : null)
                    .setParameter("error", channelResult.error())
                    .executeUpdate();
        }

        if (!anySent) {
            throw new BusinessException(error == null ? "Unable to send payment reminder" : error);
        }

        TenantContext.userId().ifPresent(userId -> notifications.send(NotificationRequest.of(
                        NotificationType.SYSTEM,
                        NotificationRecipient.user(userId, null, null),
                        "Payment reminder sent",
                        "Reminder for invoice " + invoice.invoiceNumber() + " was sent.")
                .channel(NotificationChannel.IN_APP)
                .organizationId(invoice.organizationId())
                .related("SalesInvoice", invoice.id())));

        return new SendReminderResponse(reminderId, true, "Reminder sent");
    }

    private InvoiceSnapshot loadInvoice(UUID invoiceId) {
        UUID orgId = TenantContext.getOrganizationId();
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                        """
                        select si.id, si.organization_id, si.invoice_number, si.outstanding_amount,
                               c.email, c.phone, coalesce(c.customer_name, 'Customer')
                        from sales_invoices si
                        left join customers c on c.id = si.customer_id
                        where si.id = :id and si.organization_id = :org
                        """)
                .setParameter("id", invoiceId)
                .setParameter("org", orgId)
                .getResultList();
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found");
        }
        return toSnapshot(rows.get(0));
    }

    @SuppressWarnings("unchecked")
    private List<InvoiceSnapshot> findDueInvoices(UUID orgId, LocalDate dueDate) {
        List<Object[]> rows = em.createNativeQuery(
                        """
                        select si.id, si.organization_id, si.invoice_number, si.outstanding_amount,
                               c.email, c.phone, coalesce(c.customer_name, 'Customer')
                        from sales_invoices si
                        left join customers c on c.id = si.customer_id
                        where si.organization_id = :org
                          and si.status not in ('DRAFT','CANCELLED')
                          and si.outstanding_amount > 0
                          and si.due_date = :due
                        """)
                .setParameter("org", orgId)
                .setParameter("due", dueDate)
                .getResultList();
        List<InvoiceSnapshot> out = new ArrayList<>();
        for (Object[] row : rows) out.add(toSnapshot(row));
        return out;
    }

    private boolean alreadySentToday(UUID orgId, UUID invoiceId, UUID ruleId, NotificationChannel channel) {
        Number count = (Number) em.createNativeQuery(
                        """
                        select count(*) from payment_reminders
                        where organization_id = :org
                          and invoice_id = :invoice
                          and rule_id = :rule
                          and channel = :channel
                          and status = 'SENT'
                          and sent_at::date = current_date
                        """)
                .setParameter("org", orgId)
                .setParameter("invoice", invoiceId)
                .setParameter("rule", ruleId)
                .setParameter("channel", channel.name())
                .getSingleResult();
        return count != null && count.longValue() > 0;
    }

    private InvoiceSnapshot toSnapshot(Object[] row) {
        UUID id = (UUID) row[0];
        UUID orgId = (UUID) row[1];
        String invoiceNumber = row[2] == null ? id.toString() : row[2].toString();
        String outstanding = row[3] == null ? "0" : ((row[3] instanceof BigDecimal bd) ? bd.toPlainString() : row[3].toString());
        String email = row[4] == null ? null : row[4].toString();
        String phone = row[5] == null ? null : row[5].toString();
        String customerName = row[6] == null ? "Customer" : row[6].toString();
        return new InvoiceSnapshot(id, orgId, invoiceNumber, outstanding, email, phone, customerName);
    }

    private Set<NotificationChannel> resolveChannels(List<String> raw) {
        Set<NotificationChannel> channels = EnumSet.noneOf(NotificationChannel.class);
        if (raw == null || raw.isEmpty()) {
            channels.add(NotificationChannel.EMAIL);
            return channels;
        }
        for (String value : raw) {
            if (value == null || value.isBlank()) continue;
            channels.add(parseChannel(value));
        }
        return channels;
    }

    private NotificationChannel parseChannel(String value) {
        try {
            NotificationChannel channel = NotificationChannel.valueOf(value.trim().toUpperCase(Locale.ROOT));
            if (channel == NotificationChannel.SMS) {
                throw new BusinessException("SMS reminders are not supported yet");
            }
            if (channel == NotificationChannel.IN_APP) {
                throw new BusinessException("In-app reminders are for users, not customers");
            }
            return channel;
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("Unsupported reminder channel: " + value);
        }
    }

    private void applyRule(PaymentReminderRule rule, ReminderRuleRequest request) {
        String offsetType = request.offsetType().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("BEFORE_DUE", "AFTER_DUE", "ON_DUE").contains(offsetType)) {
            throw new BusinessException("offsetType must be BEFORE_DUE, AFTER_DUE, or ON_DUE");
        }
        NotificationChannel channel = parseChannel(request.channel());
        rule.setName(request.name().trim());
        rule.setDaysOffset(request.daysOffset());
        rule.setOffsetType(offsetType);
        rule.setChannel(channel.name());
        rule.setEnabled(request.enabled() == null || request.enabled());
        rule.setSubjectTemplate(request.subjectTemplate());
        rule.setBodyTemplate(request.bodyTemplate());
        TenantContext.userId().ifPresent(rule::setUpdatedBy);
        if (rule.getCreatedBy() == null) {
            TenantContext.userId().ifPresent(rule::setCreatedBy);
        }
    }

    private ReminderRuleResponse toRuleResponse(PaymentReminderRule rule) {
        return new ReminderRuleResponse(
                rule.getId(),
                rule.getName(),
                rule.getDaysOffset(),
                rule.getOffsetType(),
                rule.getChannel(),
                rule.isEnabled(),
                rule.getSubjectTemplate(),
                rule.getBodyTemplate());
    }

    private String applyTemplate(String template, String fallback, InvoiceSnapshot invoice) {
        if (template == null || template.isBlank()) return fallback;
        return template
                .replace("{{invoiceNumber}}", invoice.invoiceNumber())
                .replace("{{outstanding}}", invoice.outstanding())
                .replace("{{customerName}}", invoice.customerName());
    }

    private record InvoiceSnapshot(
            UUID id,
            UUID organizationId,
            String invoiceNumber,
            String outstanding,
            String email,
            String phone,
            String customerName) {}
}
