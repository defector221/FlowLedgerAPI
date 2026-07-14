package com.flowledger.marketing.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowledger.common.exception.BusinessException;
import com.flowledger.common.service.OrganizationScopedService;
import com.flowledger.common.util.MergeTags;
import com.flowledger.customer.entity.Customer;
import com.flowledger.customer.repository.CustomerRepository;
import com.flowledger.emailtemplate.entity.EmailTemplate;
import com.flowledger.emailtemplate.repository.EmailTemplateRepository;
import com.flowledger.lead.entity.Lead;
import com.flowledger.lead.repository.LeadRepository;
import com.flowledger.marketing.dto.CampaignDtos.*;
import com.flowledger.marketing.entity.MarketingCampaign;
import com.flowledger.marketing.entity.MarketingCampaignRecipient;
import com.flowledger.marketing.repository.MarketingCampaignRecipientRepository;
import com.flowledger.marketing.repository.MarketingCampaignRepository;
import com.flowledger.notification.EmailNotificationService;
import jakarta.persistence.criteria.Predicate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@Transactional
public class MarketingCampaignService extends OrganizationScopedService {
    public static final int MAX_RECIPIENTS_PER_CAMPAIGN = 5_000;
    private static final int BATCH_SIZE = 50;

    private final MarketingCampaignRepository campaigns;
    private final MarketingCampaignRecipientRepository recipients;
    private final EmailTemplateRepository emailTemplates;
    private final LeadRepository leads;
    private final CustomerRepository customers;
    private final EmailNotificationService emailNotifications;

    public MarketingCampaignService(
            MarketingCampaignRepository campaigns,
            MarketingCampaignRecipientRepository recipients,
            EmailTemplateRepository emailTemplates,
            LeadRepository leads,
            CustomerRepository customers,
            EmailNotificationService emailNotifications) {
        this.campaigns = campaigns;
        this.recipients = recipients;
        this.emailTemplates = emailTemplates;
        this.leads = leads;
        this.customers = customers;
        this.emailNotifications = emailNotifications;
    }

    @Transactional(readOnly = true)
    public List<CampaignResponse> list() {
        return campaigns.findByOrganizationIdOrderByCreatedAtDesc(orgId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CampaignResponse get(UUID id) {
        return toResponse(required(campaigns.findByIdAndOrganizationId(id, orgId()), "Campaign"));
    }

    @Transactional(readOnly = true)
    public Page<RecipientResponse> listRecipients(UUID campaignId, Pageable pageable) {
        MarketingCampaign campaign = required(campaigns.findByIdAndOrganizationId(campaignId, orgId()), "Campaign");
        return recipients
                .findByCampaignIdOrderByCreatedAtAsc(campaign.getId(), pageable)
                .map(this::toRecipient);
    }

    @Transactional(readOnly = true)
    public AudiencePreviewResponse previewAudience(UpsertCampaignRequest request) {
        List<ResolvedRecipient> resolved = resolveAudience(request);
        return new AudiencePreviewResponse(resolved.size(), MAX_RECIPIENTS_PER_CAMPAIGN);
    }

    public CampaignResponse create(UpsertCampaignRequest request) {
        EmailTemplate template = required(
                emailTemplates.findByIdAndOrganizationId(request.emailTemplateId(), orgId()), "Email template");
        MarketingCampaign campaign = new MarketingCampaign();
        campaign.setOrganizationId(orgId());
        applyDraft(campaign, request, template.getId());
        return toResponse(campaigns.save(campaign));
    }

    public CampaignResponse update(UUID id, UpsertCampaignRequest request) {
        MarketingCampaign campaign = required(campaigns.findByIdAndOrganizationId(id, orgId()), "Campaign");
        if (!"DRAFT".equals(campaign.getStatus()) && !"CANCELLED".equals(campaign.getStatus())) {
            throw new BusinessException("Only draft or cancelled campaigns can be edited");
        }
        EmailTemplate template = required(
                emailTemplates.findByIdAndOrganizationId(request.emailTemplateId(), orgId()), "Email template");
        campaign.setStatus("DRAFT");
        applyDraft(campaign, request, template.getId());
        return toResponse(campaigns.save(campaign));
    }

    public CampaignResponse schedule(UUID id, ScheduleRequest request) {
        MarketingCampaign campaign = required(campaigns.findByIdAndOrganizationId(id, orgId()), "Campaign");
        if (!"DRAFT".equals(campaign.getStatus()) && !"CANCELLED".equals(campaign.getStatus())) {
            throw new BusinessException("Campaign cannot be scheduled from status " + campaign.getStatus());
        }
        recipients.deleteByCampaignId(campaign.getId());
        List<ResolvedRecipient> resolved = resolveAudience(toUpsert(campaign));
        if (resolved.isEmpty()) {
            throw new BusinessException("No recipients matched the campaign audience");
        }
        for (ResolvedRecipient item : resolved) {
            MarketingCampaignRecipient recipient = new MarketingCampaignRecipient();
            recipient.setCampaignId(campaign.getId());
            recipient.setRecipientType(item.type());
            recipient.setRecipientId(item.id());
            recipient.setEmail(item.email());
            recipient.setStatus(item.email() == null || item.email().isBlank() ? "SKIPPED" : "PENDING");
            if ("SKIPPED".equals(recipient.getStatus())) {
                recipient.setErrorMessage("Missing email");
            }
            recipients.save(recipient);
        }
        campaign.setTotalCount(resolved.size());
        campaign.setSentCount(0);
        campaign.setFailedCount(0);
        campaign.setSkippedCount((int) resolved.stream()
                .filter(r -> r.email() == null || r.email().isBlank())
                .count());
        campaign.setStatus("SCHEDULED");
        campaign.setScheduledAt(
                request == null || request.scheduledAt() == null ? OffsetDateTime.now() : request.scheduledAt());
        campaign.setStartedAt(null);
        campaign.setCompletedAt(null);
        return toResponse(campaigns.save(campaign));
    }

    public CampaignResponse cancel(UUID id) {
        MarketingCampaign campaign = required(campaigns.findByIdAndOrganizationId(id, orgId()), "Campaign");
        if ("SENT".equals(campaign.getStatus()) || "CANCELLED".equals(campaign.getStatus())) {
            return toResponse(campaign);
        }
        campaign.setStatus("CANCELLED");
        return toResponse(campaigns.save(campaign));
    }

    @Scheduled(fixedDelayString = "60000")
    public void processDueCampaigns() {
        OffsetDateTime now = OffsetDateTime.now();
        List<MarketingCampaign> due = campaigns.findDueCampaigns(now);
        for (MarketingCampaign campaign : due) {
            try {
                processCampaignBatch(campaign, now);
            } catch (Exception ex) {
                log.warn("Campaign {} batch failed: {}", campaign.getId(), ex.getMessage());
            }
        }
    }

    private void processCampaignBatch(MarketingCampaign campaign, OffsetDateTime now) {
        if ("SCHEDULED".equals(campaign.getStatus())) {
            campaign.setStatus("SENDING");
            campaign.setStartedAt(now);
            campaigns.save(campaign);
        }
        if (!"SENDING".equals(campaign.getStatus())) {
            return;
        }
        EmailTemplate template = emailTemplates
                .findByIdAndOrganizationId(campaign.getEmailTemplateId(), campaign.getOrganizationId())
                .orElse(null);
        if (template == null || template.getHtml() == null || template.getHtml().isBlank()) {
            campaign.setStatus("CANCELLED");
            campaigns.save(campaign);
            log.warn("Campaign {} cancelled: missing email template HTML", campaign.getId());
            return;
        }

        List<MarketingCampaignRecipient> batch =
                recipients.findTop50ByCampaignIdAndStatusOrderByCreatedAtAsc(campaign.getId(), "PENDING");
        if (batch.isEmpty()) {
            long pending = recipients.countByCampaignIdAndStatus(campaign.getId(), "PENDING");
            if (pending == 0) {
                campaign.setStatus("SENT");
                campaign.setCompletedAt(now);
                campaigns.save(campaign);
            }
            return;
        }

        int sent = 0;
        int failed = 0;
        int skipped = 0;
        for (MarketingCampaignRecipient recipient : batch) {
            if (recipient.getEmail() == null || recipient.getEmail().isBlank()) {
                recipient.setStatus("SKIPPED");
                recipient.setErrorMessage("Missing email");
                skipped++;
                recipients.save(recipient);
                continue;
            }
            try {
                Map<String, String> tags = mergeTags(campaign.getOrganizationId(), recipient);
                String subject = MergeTags.apply(template.getSubject(), tags);
                String html = MergeTags.apply(template.getHtml(), tags);
                emailNotifications.sendMarketingHtml(recipient.getEmail(), subject, html);
                recipient.setStatus("SENT");
                recipient.setSentAt(now);
                sent++;
            } catch (Exception ex) {
                recipient.setStatus("FAILED");
                recipient.setErrorMessage(ex.getMessage());
                failed++;
            }
            recipients.save(recipient);
        }
        campaign.setSentCount(campaign.getSentCount() + sent);
        campaign.setFailedCount(campaign.getFailedCount() + failed);
        campaign.setSkippedCount(campaign.getSkippedCount() + skipped);
        if (recipients.countByCampaignIdAndStatus(campaign.getId(), "PENDING") == 0) {
            campaign.setStatus("SENT");
            campaign.setCompletedAt(now);
        }
        campaigns.save(campaign);
        if (batch.size() >= BATCH_SIZE) {
            log.debug("Campaign {} processed batch of {}", campaign.getId(), batch.size());
        }
    }

    private void applyDraft(MarketingCampaign campaign, UpsertCampaignRequest request, UUID templateId) {
        String audience = request.audienceType().toUpperCase(Locale.ROOT);
        if (!Set.of("LEAD", "CUSTOMER", "MIXED").contains(audience)) {
            throw new BusinessException("Unsupported audience type: " + audience);
        }
        campaign.setName(request.name().trim());
        campaign.setAudienceType(audience);
        campaign.setFilterJson(request.filterJson());
        campaign.setEmailTemplateId(templateId);
    }

    private UpsertCampaignRequest toUpsert(MarketingCampaign campaign) {
        return new UpsertCampaignRequest(
                campaign.getName(),
                campaign.getAudienceType(),
                campaign.getFilterJson(),
                campaign.getEmailTemplateId(),
                null,
                null);
    }

    private List<ResolvedRecipient> resolveAudience(UpsertCampaignRequest request) {
        String audience = request.audienceType().toUpperCase(Locale.ROOT);
        JsonNode filter = request.filterJson();
        String leadStatus = text(filter, "leadStatus");
        String search = text(filter, "search");
        boolean includeArchived =
                filter != null && filter.path("includeArchivedCustomers").asBoolean(false);

        Set<String> seen = new HashSet<>();
        List<ResolvedRecipient> out = new ArrayList<>();

        if (("LEAD".equals(audience) || "MIXED".equals(audience))
                && (request.leadIds() == null || request.leadIds().isEmpty())) {
            Specification<Lead> spec = (root, query, cb) -> {
                List<Predicate> preds = new ArrayList<>();
                preds.add(cb.equal(root.get("organizationId"), orgId()));
                if (leadStatus != null && !leadStatus.isBlank()) {
                    preds.add(cb.equal(root.get("status"), leadStatus.toUpperCase(Locale.ROOT)));
                }
                if (search != null && !search.isBlank()) {
                    String like = "%" + search.toLowerCase(Locale.ROOT) + "%";
                    preds.add(cb.or(
                            cb.like(cb.lower(root.get("leadName")), like),
                            cb.like(cb.lower(cb.coalesce(root.get("email"), "")), like),
                            cb.like(cb.lower(cb.coalesce(root.get("companyName"), "")), like)));
                }
                return cb.and(preds.toArray(Predicate[]::new));
            };
            for (Lead lead : leads.findAll(spec)) {
                addRecipient(out, seen, "LEAD", lead.getId(), lead.getEmail());
                if (out.size() >= MAX_RECIPIENTS_PER_CAMPAIGN) {
                    return out;
                }
            }
        }

        if (request.leadIds() != null) {
            for (UUID leadId : request.leadIds()) {
                leads.findByIdAndOrganizationId(leadId, orgId())
                        .ifPresent(lead -> addRecipient(out, seen, "LEAD", lead.getId(), lead.getEmail()));
                if (out.size() >= MAX_RECIPIENTS_PER_CAMPAIGN) {
                    return out;
                }
            }
        }

        if (("CUSTOMER".equals(audience) || "MIXED".equals(audience))
                && (request.customerIds() == null || request.customerIds().isEmpty())) {
            Specification<Customer> spec = (root, query, cb) -> {
                List<Predicate> preds = new ArrayList<>();
                preds.add(cb.equal(root.get("organizationId"), orgId()));
                preds.add(cb.isNotNull(root.get("email")));
                preds.add(cb.notEqual(cb.trim(root.get("email")), ""));
                if (!includeArchived) {
                    preds.add(cb.isFalse(root.get("archived")));
                }
                if (search != null && !search.isBlank()) {
                    String like = "%" + search.toLowerCase(Locale.ROOT) + "%";
                    preds.add(cb.or(
                            cb.like(cb.lower(root.get("customerName")), like),
                            cb.like(cb.lower(cb.coalesce(root.get("email"), "")), like),
                            cb.like(cb.lower(cb.coalesce(root.get("companyName"), "")), like)));
                }
                return cb.and(preds.toArray(Predicate[]::new));
            };
            for (Customer customer : customers.findAll(spec)) {
                addRecipient(out, seen, "CUSTOMER", customer.getId(), customer.getEmail());
                if (out.size() >= MAX_RECIPIENTS_PER_CAMPAIGN) {
                    return out;
                }
            }
        }

        if (request.customerIds() != null) {
            for (UUID customerId : request.customerIds()) {
                customers
                        .findByIdAndOrganizationId(customerId, orgId())
                        .ifPresent(
                                customer -> addRecipient(out, seen, "CUSTOMER", customer.getId(), customer.getEmail()));
                if (out.size() >= MAX_RECIPIENTS_PER_CAMPAIGN) {
                    return out;
                }
            }
        }

        return out;
    }

    private void addRecipient(List<ResolvedRecipient> out, Set<String> seen, String type, UUID id, String email) {
        String key = type + ":" + id;
        if (seen.add(key)) {
            out.add(new ResolvedRecipient(type, id, email));
        }
    }

    private Map<String, String> mergeTags(UUID organizationId, MarketingCampaignRecipient recipient) {
        Map<String, String> tags = new HashMap<>();
        if ("LEAD".equals(recipient.getRecipientType())) {
            leads.findByIdAndOrganizationId(recipient.getRecipientId(), organizationId)
                    .ifPresent(lead -> {
                        tags.put("leadName", nullToEmpty(lead.getLeadName()));
                        tags.put("firstName", firstName(lead.getLeadName()));
                        tags.put("company", nullToEmpty(lead.getCompanyName()));
                        tags.put("email", nullToEmpty(lead.getEmail()));
                        tags.put("phone", nullToEmpty(lead.getPhone()));
                    });
        } else if ("CUSTOMER".equals(recipient.getRecipientType())) {
            customers
                    .findByIdAndOrganizationId(recipient.getRecipientId(), organizationId)
                    .ifPresent(customer -> {
                        tags.put("customerName", nullToEmpty(customer.getCustomerName()));
                        tags.put("firstName", firstName(customer.getCustomerName()));
                        tags.put("company", nullToEmpty(customer.getCompanyName()));
                        tags.put("email", nullToEmpty(customer.getEmail()));
                        tags.put("phone", nullToEmpty(customer.getPhone()));
                    });
        }
        tags.putIfAbsent("email", nullToEmpty(recipient.getEmail()));
        return tags;
    }

    private static String text(JsonNode filter, String field) {
        if (filter == null
                || filter.path(field).isMissingNode()
                || filter.path(field).isNull()) {
            return null;
        }
        String value = filter.path(field).asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private static String firstName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "";
        }
        return fullName.trim().split("\\s+")[0];
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private CampaignResponse toResponse(MarketingCampaign campaign) {
        return new CampaignResponse(
                campaign.getId(),
                campaign.getName(),
                campaign.getStatus(),
                campaign.getAudienceType(),
                campaign.getFilterJson(),
                campaign.getEmailTemplateId(),
                campaign.getScheduledAt(),
                campaign.getStartedAt(),
                campaign.getCompletedAt(),
                campaign.getTotalCount(),
                campaign.getSentCount(),
                campaign.getFailedCount(),
                campaign.getSkippedCount(),
                campaign.getCreatedAt(),
                campaign.getUpdatedAt());
    }

    private RecipientResponse toRecipient(MarketingCampaignRecipient recipient) {
        return new RecipientResponse(
                recipient.getId(),
                recipient.getRecipientType(),
                recipient.getRecipientId(),
                recipient.getEmail(),
                recipient.getStatus(),
                recipient.getErrorMessage(),
                recipient.getSentAt());
    }

    private record ResolvedRecipient(String type, UUID id, String email) {}
}
