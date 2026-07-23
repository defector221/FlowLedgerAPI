package com.flowledger.retail.service;

import static com.flowledger.retail.dto.RetailDtos.*;

import com.flowledger.common.tenant.TenantContext;
import com.flowledger.retail.entity.RetailLabelTemplate;
import com.flowledger.retail.repository.RetailLabelTemplateRepository;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class RetailLabelService {
    private final RetailModuleGuard guard;
    private final RetailLabelTemplateRepository templates;

    public RetailLabelService(RetailModuleGuard guard, RetailLabelTemplateRepository templates) {
        this.guard = guard;
        this.templates = templates;
    }

    @Transactional(readOnly = true)
    public List<LabelTemplateResponse> listTemplates() {
        return templates.findByOrganizationIdAndDeletedFalseOrderByNameAsc(org()).stream()
                .map(this::map)
                .toList();
    }

    @Transactional(readOnly = true)
    public LabelTemplateResponse getTemplate(UUID id) {
        return map(load(id));
    }

    public LabelTemplateResponse createTemplate(LabelTemplateRequest r) {
        String code = code(r.code());
        if (templates.existsByOrganizationIdAndCodeIgnoreCaseAndDeletedFalse(org(), code)) {
            conflict("Label template code already exists");
        }
        RetailLabelTemplate e = new RetailLabelTemplate();
        e.setOrganizationId(org());
        e.setCode(code);
        e.setName(r.name());
        e.setLabelType(r.labelType() == null || r.labelType().isBlank() ? "SHELF" : r.labelType());
        e.setTemplateBody(r.templateBody());
        audit(e, true);
        return map(templates.save(e));
    }

    public LabelTemplateResponse updateTemplate(UUID id, LabelTemplateRequest r) {
        RetailLabelTemplate e = load(id);
        e.setName(r.name());
        if (r.labelType() != null && !r.labelType().isBlank()) {
            e.setLabelType(r.labelType());
        }
        e.setTemplateBody(r.templateBody());
        audit(e, false);
        return map(templates.save(e));
    }

    public void deleteTemplate(UUID id) {
        RetailLabelTemplate e = load(id);
        e.setDeleted(true);
        audit(e, false);
    }

    @Transactional(readOnly = true)
    public RenderLabelResponse renderLabel(RenderLabelRequest r) {
        RetailLabelTemplate template = load(r.templateId());
        String rendered = template.getTemplateBody();
        Map<String, String> values = r.values() == null ? Map.of() : r.values();
        rendered = replace(rendered, "name", values.getOrDefault("name", ""));
        rendered = replace(rendered, "price", values.getOrDefault("price", ""));
        rendered = replace(rendered, "barcode", values.getOrDefault("barcode", ""));
        for (Map.Entry<String, String> entry : values.entrySet()) {
            rendered = replace(rendered, entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
        }
        return new RenderLabelResponse(template.getId(), rendered);
    }

    private String replace(String body, String key, String value) {
        return body.replace("{{" + key + "}}", value == null ? "" : value);
    }

    private RetailLabelTemplate load(UUID id) {
        return templates
                .findByIdAndOrganizationIdAndDeletedFalse(id, org())
                .orElseThrow(() -> notFound("Label template not found"));
    }

    private LabelTemplateResponse map(RetailLabelTemplate e) {
        return new LabelTemplateResponse(
                e.getId(), e.getCode(), e.getName(), e.getLabelType(), e.getTemplateBody(), e.getVersion());
    }

    private UUID org() {
        return guard.ensureEnabled();
    }

    private String code(String provided) {
        return provided.trim().toUpperCase(Locale.ROOT);
    }

    private void audit(com.flowledger.common.entity.AuditedEntity e, boolean created) {
        TenantContext.userId().ifPresent(u -> {
            if (created) {
                e.setCreatedBy(u);
            }
            e.setUpdatedBy(u);
        });
    }

    private ResponseStatusException notFound(String m) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, m);
    }

    private void conflict(String m) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, m);
    }
}
