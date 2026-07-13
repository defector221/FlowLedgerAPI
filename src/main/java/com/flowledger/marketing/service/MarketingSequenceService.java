package com.flowledger.marketing.service;

import com.flowledger.common.exception.BusinessException;
import com.flowledger.common.service.OrganizationScopedService;
import com.flowledger.lead.entity.Lead;
import com.flowledger.lead.repository.LeadRepository;
import com.flowledger.marketing.dto.MarketingDtos.*;
import com.flowledger.marketing.entity.MarketingEnrollment;
import com.flowledger.marketing.entity.MarketingSend;
import com.flowledger.marketing.entity.MarketingSequence;
import com.flowledger.marketing.entity.MarketingSequenceStep;
import com.flowledger.marketing.repository.MarketingEnrollmentRepository;
import com.flowledger.marketing.repository.MarketingSendRepository;
import com.flowledger.marketing.repository.MarketingSequenceRepository;
import com.flowledger.notification.EmailNotificationService;
import com.flowledger.notification.WhatsAppNotificationService;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@Transactional
public class MarketingSequenceService extends OrganizationScopedService {
    private final MarketingSequenceRepository sequences;
    private final MarketingEnrollmentRepository enrollments;
    private final MarketingSendRepository sends;
    private final LeadRepository leads;
    private final EmailNotificationService emailNotifications;
    private final WhatsAppNotificationService whatsAppNotifications;

    public MarketingSequenceService(
            MarketingSequenceRepository sequences,
            MarketingEnrollmentRepository enrollments,
            MarketingSendRepository sends,
            LeadRepository leads,
            EmailNotificationService emailNotifications,
            WhatsAppNotificationService whatsAppNotifications) {
        this.sequences = sequences;
        this.enrollments = enrollments;
        this.sends = sends;
        this.leads = leads;
        this.emailNotifications = emailNotifications;
        this.whatsAppNotifications = whatsAppNotifications;
    }

    @Transactional(readOnly = true)
    public List<SequenceResponse> list() {
        return sequences.findByOrganizationIdOrderByCreatedAtDesc(orgId()).stream()
                .map(this::toSequenceResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SequenceResponse get(UUID id) {
        MarketingSequence sequence = required(sequences.findDetailedByIdAndOrganizationId(id, orgId()), "Sequence");
        return toSequenceResponse(sequence);
    }

    public SequenceResponse create(CreateSequence dto) {
        MarketingSequence sequence = new MarketingSequence();
        sequence.setOrganizationId(orgId());
        sequence.setName(dto.name());
        sequence.setDescription(dto.description());
        sequence.setTriggerType(dto.triggerType().toUpperCase(Locale.ROOT));
        sequence.setStatus(
                dto.status() == null || dto.status().isBlank() ? "DRAFT" : dto.status().toUpperCase(Locale.ROOT));

        List<MarketingSequenceStep> steps = new ArrayList<>();
        int order = 1;
        for (StepCreate stepDto : dto.steps()) {
            MarketingSequenceStep step = new MarketingSequenceStep();
            step.setSequence(sequence);
            step.setStepOrder(order++);
            step.setDelayDays(stepDto.delayDays());
            step.setChannel(stepDto.channel().toUpperCase(Locale.ROOT));
            step.setSubjectTemplate(stepDto.subject());
            step.setBodyTemplate(stepDto.body());
            steps.add(step);
        }
        sequence.getSteps().clear();
        sequence.getSteps().addAll(steps);
        return toSequenceResponse(sequences.save(sequence));
    }

    public EnrollmentResponse enrollLead(UUID sequenceId, UUID leadId) {
        MarketingSequence sequence =
                required(sequences.findDetailedByIdAndOrganizationId(sequenceId, orgId()), "Sequence");
        Lead lead = required(leads.findByIdAndOrganizationId(leadId, orgId()), "Lead");
        return toEnrollmentResponse(enroll(sequence, lead));
    }

    /** Auto-enroll a newly created lead into active LEAD_CREATED sequences for the org. */
    public void autoEnrollLeadCreated(Lead lead) {
        List<MarketingSequence> active = sequences.findByOrganizationIdAndTriggerTypeAndStatus(
                lead.getOrganizationId(), "LEAD_CREATED", "ACTIVE");
        for (MarketingSequence sequence : active) {
            try {
                MarketingSequence detailed = sequences
                        .findDetailedByIdAndOrganizationId(sequence.getId(), lead.getOrganizationId())
                        .orElse(sequence);
                enroll(detailed, lead);
            } catch (Exception ex) {
                log.warn(
                        "Auto-enroll lead {} into sequence {} failed: {}",
                        lead.getId(),
                        sequence.getId(),
                        ex.getMessage());
            }
        }
    }

    public EnrollmentResponse cancelEnrollment(UUID enrollmentId) {
        MarketingEnrollment enrollment =
                required(enrollments.findByIdAndOrganizationId(enrollmentId, orgId()), "Enrollment");
        if ("CANCELLED".equals(enrollment.getStatus()) || "COMPLETED".equals(enrollment.getStatus())) {
            return toEnrollmentResponse(enrollment);
        }
        enrollment.setStatus("CANCELLED");
        return toEnrollmentResponse(enrollments.save(enrollment));
    }

    /** Hourly: send due marketing steps for active enrollments. */
    @Scheduled(cron = "0 15 * * * *")
    public void processDueSteps() {
        OffsetDateTime now = OffsetDateTime.now();
        List<MarketingEnrollment> active = enrollments.findActiveEnrollments();
        log.debug("Marketing processDueSteps scanning {} active enrollments", active.size());
        for (MarketingEnrollment enrollment : active) {
            try {
                processEnrollment(enrollment, now);
            } catch (Exception ex) {
                log.warn("Failed processing marketing enrollment {}: {}", enrollment.getId(), ex.getMessage());
            }
        }
    }

    private MarketingEnrollment enroll(MarketingSequence sequence, Lead lead) {
        if (!"ACTIVE".equals(sequence.getStatus()) && !"DRAFT".equals(sequence.getStatus())) {
            throw new BusinessException("Sequence is not enrollable (status=" + sequence.getStatus() + ")");
        }
        if (enrollments.existsBySequenceIdAndRecipientTypeAndRecipientIdAndStatus(
                sequence.getId(), "LEAD", lead.getId(), "ACTIVE")) {
            throw new BusinessException("Lead is already enrolled in this sequence");
        }
        MarketingEnrollment enrollment = new MarketingEnrollment();
        enrollment.setOrganizationId(lead.getOrganizationId());
        enrollment.setSequenceId(sequence.getId());
        enrollment.setRecipientType("LEAD");
        enrollment.setRecipientId(lead.getId());
        enrollment.setEmail(lead.getEmail());
        enrollment.setPhone(lead.getPhone());
        enrollment.setStatus("ACTIVE");
        enrollment.setCurrentStep(0);
        return enrollments.save(enrollment);
    }

    private void processEnrollment(MarketingEnrollment enrollment, OffsetDateTime now) {
        MarketingSequence sequence = sequences
                .findDetailedByIdAndOrganizationId(enrollment.getSequenceId(), enrollment.getOrganizationId())
                .orElse(null);
        if (sequence == null || sequence.getSteps().isEmpty()) {
            enrollment.setStatus("COMPLETED");
            enrollment.setCompletedAt(now);
            enrollments.save(enrollment);
            return;
        }
        List<MarketingSequenceStep> ordered = sequence.getSteps().stream()
                .sorted(Comparator.comparingInt(MarketingSequenceStep::getStepOrder))
                .toList();
        int nextIndex = enrollment.getCurrentStep();
        if (nextIndex >= ordered.size()) {
            enrollment.setStatus("COMPLETED");
            enrollment.setCompletedAt(now);
            enrollments.save(enrollment);
            return;
        }
        MarketingSequenceStep step = ordered.get(nextIndex);
        int cumulativeDelay = 0;
        for (int i = 0; i <= nextIndex; i++) {
            cumulativeDelay += ordered.get(i).getDelayDays();
        }
        OffsetDateTime dueAt = enrollment.getEnrolledAt().plusDays(cumulativeDelay);
        if (dueAt.isAfter(now)) {
            return;
        }
        dispatchStep(enrollment, step, now);
        enrollment.setCurrentStep(nextIndex + 1);
        if (enrollment.getCurrentStep() >= ordered.size()) {
            enrollment.setStatus("COMPLETED");
            enrollment.setCompletedAt(now);
        }
        enrollments.save(enrollment);
    }

    private void dispatchStep(MarketingEnrollment enrollment, MarketingSequenceStep step, OffsetDateTime now) {
        String channel = step.getChannel() == null ? "EMAIL" : step.getChannel().toUpperCase(Locale.ROOT);
        String subject = step.getSubjectTemplate() == null ? "FlowLedger update" : step.getSubjectTemplate();
        String body = step.getBodyTemplate() == null ? "" : step.getBodyTemplate();
        MarketingSend send = new MarketingSend();
        send.setOrganizationId(enrollment.getOrganizationId());
        send.setEnrollmentId(enrollment.getId());
        send.setStepId(step.getId());
        send.setChannel(channel);
        send.setSubject(subject);
        send.setBody(body);
        send.setScheduledAt(now);

        try {
            switch (channel) {
                case "EMAIL" -> {
                    String recipient = enrollment.getEmail();
                    send.setRecipient(recipient);
                    if (recipient == null || recipient.isBlank()) {
                        send.setStatus("SKIPPED");
                        send.setErrorMessage("No email on enrollment");
                    } else {
                        emailNotifications.sendMarketing(recipient, subject, body);
                        send.setStatus("SENT");
                        send.setSentAt(now);
                    }
                }
                case "WHATSAPP" -> {
                    String recipient = enrollment.getPhone();
                    send.setRecipient(recipient);
                    if (recipient == null || recipient.isBlank()) {
                        send.setStatus("SKIPPED");
                        send.setErrorMessage("No phone on enrollment");
                    } else {
                        whatsAppNotifications.sendMarketing(recipient, subject + "\n" + body);
                        send.setStatus("SENT");
                        send.setSentAt(now);
                    }
                }
                default -> {
                    send.setRecipient(enrollment.getEmail() != null ? enrollment.getEmail() : enrollment.getPhone());
                    send.setStatus("SKIPPED");
                    send.setErrorMessage("Channel not implemented: " + channel);
                }
            }
        } catch (Exception ex) {
            send.setStatus("FAILED");
            send.setErrorMessage(ex.getMessage());
            log.warn("Marketing send failed for enrollment {}: {}", enrollment.getId(), ex.getMessage());
        }
        sends.save(send);
    }

    private SequenceResponse toSequenceResponse(MarketingSequence sequence) {
        List<StepResponse> steps = sequence.getSteps() == null
                ? List.of()
                : sequence.getSteps().stream()
                        .sorted(Comparator.comparingInt(MarketingSequenceStep::getStepOrder))
                        .map(step -> new StepResponse(
                                step.getId(),
                                step.getStepOrder(),
                                step.getDelayDays(),
                                step.getChannel(),
                                step.getSubjectTemplate(),
                                step.getBodyTemplate()))
                        .toList();
        return new SequenceResponse(
                sequence.getId(),
                sequence.getName(),
                sequence.getDescription(),
                sequence.getStatus(),
                sequence.getTriggerType(),
                steps,
                sequence.getCreatedAt(),
                sequence.getUpdatedAt());
    }

    private EnrollmentResponse toEnrollmentResponse(MarketingEnrollment enrollment) {
        return new EnrollmentResponse(
                enrollment.getId(),
                enrollment.getSequenceId(),
                enrollment.getRecipientType(),
                enrollment.getRecipientId(),
                enrollment.getEmail(),
                enrollment.getPhone(),
                enrollment.getStatus(),
                enrollment.getCurrentStep(),
                enrollment.getEnrolledAt(),
                enrollment.getCompletedAt());
    }
}
