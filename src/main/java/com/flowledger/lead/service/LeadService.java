package com.flowledger.lead.service;

import com.flowledger.common.exception.BusinessException;
import com.flowledger.common.service.OrganizationScopedService;
import com.flowledger.lead.dto.LeadDtos.*;
import com.flowledger.lead.entity.Lead;
import com.flowledger.lead.entity.LeadFollowUp;
import com.flowledger.lead.repository.LeadFollowUpRepository;
import com.flowledger.lead.repository.LeadRepository;
import com.flowledger.marketing.service.MarketingSequenceService;
import com.flowledger.notification.EmailNotificationService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@Transactional
public class LeadService extends OrganizationScopedService {
    private final LeadRepository leads;
    private final LeadFollowUpRepository followUps;
    private final EmailNotificationService emailNotifications;
    private final MarketingSequenceService marketingSequences;

    public LeadService(
            LeadRepository leads,
            LeadFollowUpRepository followUps,
            EmailNotificationService emailNotifications,
            MarketingSequenceService marketingSequences) {
        this.leads = leads;
        this.followUps = followUps;
        this.emailNotifications = emailNotifications;
        this.marketingSequences = marketingSequences;
    }

    public Response create(Create dto) {
        Lead lead = new Lead();
        lead.setOrganizationId(orgId());
        apply(
                lead,
                dto.leadName(),
                dto.companyName(),
                dto.email(),
                dto.phone(),
                dto.source(),
                dto.status(),
                dto.assignedTo(),
                dto.notes(),
                dto.estimatedValue());
        Lead saved = leads.save(lead);
        try {
            marketingSequences.autoEnrollLeadCreated(saved);
        } catch (Exception ex) {
            log.warn("Auto-enroll marketing for lead {} failed: {}", saved.getId(), ex.getMessage());
        }
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Response get(UUID id) {
        return toResponse(load(id));
    }

    public Response update(UUID id, Update dto) {
        Lead lead = load(id);
        if ("CONVERTED".equals(lead.getStatus())) {
            throw new BusinessException("Converted leads cannot be updated");
        }
        apply(
                lead,
                dto.leadName(),
                dto.companyName(),
                dto.email(),
                dto.phone(),
                dto.source(),
                dto.status(),
                dto.assignedTo(),
                dto.notes(),
                dto.estimatedValue());
        return toResponse(leads.save(lead));
    }

    public void delete(UUID id) {
        Lead lead = load(id);
        lead.setArchived(true);
        leads.save(lead);
    }

    @Transactional(readOnly = true)
    public Page<Response> list(String status, Pageable pageable) {
        Page<Lead> page = status == null || status.isBlank()
                ? leads.findByOrganizationIdAndArchivedFalse(orgId(), pageable)
                : leads.findByOrganizationIdAndStatusAndArchivedFalse(orgId(), status.toUpperCase(), pageable);
        return page.map(this::toResponse);
    }

    public FollowUpResponse addFollowUp(UUID leadId, FollowUpCreate dto) {
        Lead lead = load(leadId);
        LeadFollowUp followUp = new LeadFollowUp();
        followUp.setOrganizationId(orgId());
        followUp.setLeadId(lead.getId());
        followUp.setFollowUpAt(dto.followUpAt());
        followUp.setNotes(dto.notes());
        followUp.setStatus("PENDING");
        LeadFollowUp saved = followUps.save(followUp);
        if (lead.getEmail() != null && !lead.getEmail().isBlank()) {
            emailNotifications.sendLeadFollowUp(
                    lead.getEmail(),
                    "Follow-up scheduled for " + lead.getLeadName(),
                    dto.notes() == null ? "A follow-up has been scheduled." : dto.notes());
        }
        return toFollowUpResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<FollowUpResponse> listFollowUps(UUID leadId) {
        load(leadId);
        return followUps.findByLeadIdAndOrganizationIdOrderByFollowUpAtAsc(leadId, orgId()).stream()
                .map(this::toFollowUpResponse)
                .toList();
    }

    public FollowUpResponse completeFollowUp(UUID leadId, UUID followUpId) {
        load(leadId);
        LeadFollowUp followUp = required(followUps.findByIdAndOrganizationId(followUpId, orgId()), "Follow-up");
        if (!followUp.getLeadId().equals(leadId)) {
            throw new BusinessException("Follow-up does not belong to lead");
        }
        followUp.setStatus("COMPLETED");
        followUp.setCompletedAt(OffsetDateTime.now());
        return toFollowUpResponse(followUps.save(followUp));
    }

    /** Stub: marks lead converted; customer creation can be wired later. */
    public Response convert(UUID id, ConvertRequest request) {
        Lead lead = load(id);
        if ("CONVERTED".equals(lead.getStatus())) {
            throw new BusinessException("Lead is already converted");
        }
        lead.setStatus("CONVERTED");
        lead.setConvertedAt(OffsetDateTime.now());
        lead.setConvertedCustomerId(request == null ? null : request.customerId());
        return toResponse(leads.save(lead));
    }

    private Lead load(UUID id) {
        return required(leads.findByIdAndOrganizationId(id, orgId()), "Lead");
    }

    private void apply(
            Lead lead,
            String leadName,
            String companyName,
            String email,
            String phone,
            String source,
            String status,
            UUID assignedTo,
            String notes,
            java.math.BigDecimal estimatedValue) {
        lead.setLeadName(leadName);
        lead.setCompanyName(companyName);
        lead.setEmail(email);
        lead.setPhone(phone);
        lead.setSource(source);
        if (status != null && !status.isBlank()) {
            lead.setStatus(status.toUpperCase());
        }
        lead.setAssignedTo(assignedTo);
        lead.setNotes(notes);
        lead.setEstimatedValue(estimatedValue);
    }

    private Response toResponse(Lead lead) {
        return new Response(
                lead.getId(),
                lead.getLeadName(),
                lead.getCompanyName(),
                lead.getEmail(),
                lead.getPhone(),
                lead.getSource(),
                lead.getStatus(),
                lead.getAssignedTo(),
                lead.getNotes(),
                lead.getEstimatedValue(),
                lead.getConvertedCustomerId(),
                lead.getConvertedAt(),
                lead.getCreatedAt(),
                lead.getUpdatedAt());
    }

    private FollowUpResponse toFollowUpResponse(LeadFollowUp followUp) {
        return new FollowUpResponse(
                followUp.getId(),
                followUp.getLeadId(),
                followUp.getFollowUpAt(),
                followUp.getNotes(),
                followUp.getStatus(),
                followUp.getCompletedAt(),
                followUp.getCreatedAt());
    }
}
