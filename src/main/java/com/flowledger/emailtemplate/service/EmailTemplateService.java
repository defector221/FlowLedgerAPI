package com.flowledger.emailtemplate.service;

import com.flowledger.common.exception.BusinessException;
import com.flowledger.common.exception.ConflictException;
import com.flowledger.common.service.OrganizationScopedService;
import com.flowledger.common.util.MergeTags;
import com.flowledger.emailtemplate.dto.EmailTemplateDtos.PreviewRequest;
import com.flowledger.emailtemplate.dto.EmailTemplateDtos.PreviewResponse;
import com.flowledger.emailtemplate.dto.EmailTemplateDtos.Response;
import com.flowledger.emailtemplate.dto.EmailTemplateDtos.UpsertRequest;
import com.flowledger.emailtemplate.entity.EmailTemplate;
import com.flowledger.emailtemplate.repository.EmailTemplateRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class EmailTemplateService extends OrganizationScopedService {
    private final EmailTemplateRepository templates;

    public EmailTemplateService(EmailTemplateRepository templates) {
        this.templates = templates;
    }

    @Transactional(readOnly = true)
    public List<Response> list() {
        return templates.findByOrganizationIdAndActiveTrueOrderByUpdatedAtDesc(orgId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Response get(UUID id) {
        return toResponse(required(templates.findByIdAndOrganizationId(id, orgId()), "Email template"));
    }

    @Transactional(readOnly = true)
    public EmailTemplate requireEntity(UUID id) {
        return required(templates.findByIdAndOrganizationId(id, orgId()), "Email template");
    }

    @Transactional(readOnly = true)
    public EmailTemplate requireEntity(UUID id, UUID organizationId) {
        return required(templates.findByIdAndOrganizationId(id, organizationId), "Email template");
    }

    public Response create(UpsertRequest request) {
        if (templates.existsByOrganizationIdAndNameIgnoreCaseAndActiveTrue(
                orgId(), request.name().trim())) {
            throw new ConflictException("Email template name already exists");
        }
        EmailTemplate template = new EmailTemplate();
        template.setOrganizationId(orgId());
        template.setActive(true);
        apply(template, request);
        return toResponse(templates.save(template));
    }

    public Response update(UUID id, UpsertRequest request) {
        EmailTemplate template = required(templates.findByIdAndOrganizationId(id, orgId()), "Email template");
        if (!template.isActive()) {
            throw new BusinessException("Cannot update an archived email template");
        }
        if (templates.existsByOrganizationIdAndNameIgnoreCaseAndActiveTrueAndIdNot(
                orgId(), request.name().trim(), id)) {
            throw new ConflictException("Email template name already exists");
        }
        apply(template, request);
        return toResponse(templates.save(template));
    }

    public void delete(UUID id) {
        EmailTemplate template = required(templates.findByIdAndOrganizationId(id, orgId()), "Email template");
        template.setActive(false);
        templates.save(template);
    }

    @Transactional(readOnly = true)
    public PreviewResponse preview(UUID id, PreviewRequest request) {
        EmailTemplate template = required(templates.findByIdAndOrganizationId(id, orgId()), "Email template");
        Map<String, String> tags = new HashMap<>(MergeTags.sampleLeadTags());
        if (request != null && request.mergeTags() != null) {
            tags.putAll(request.mergeTags());
        }
        return new PreviewResponse(
                MergeTags.apply(template.getSubject(), tags), MergeTags.apply(nullToEmpty(template.getHtml()), tags));
    }

    private void apply(EmailTemplate template, UpsertRequest request) {
        if (request.html() == null || request.html().isBlank()) {
            throw new BusinessException("Exported HTML is required from the design editor");
        }
        template.setName(request.name().trim());
        template.setSubject(request.subject() == null ? "" : request.subject().trim());
        template.setDesignJson(request.designJson());
        template.setHtml(request.html());
    }

    private Response toResponse(EmailTemplate template) {
        return new Response(
                template.getId(),
                template.getName(),
                template.getSubject(),
                template.getDesignJson(),
                template.getHtml(),
                template.getCreatedAt(),
                template.getUpdatedAt());
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
