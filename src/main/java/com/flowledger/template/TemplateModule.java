package com.flowledger.template;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowledger.common.entity.AuditedEntity;
import com.flowledger.common.tenant.TenantContext;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.util.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Entity
@Table(name = "invoice_templates")
@Getter
@Setter
@NoArgsConstructor
class InvoiceTemplate extends AuditedEntity {
    @Column(name = "template_name")
    String templateName;

    String presetKey;
    boolean isDefault;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config_json", columnDefinition = "jsonb")
    JsonNode configJson;

    @Version
    Long version;
}

record TemplateRequest(@NotBlank String templateName, String presetKey, JsonNode configJson) {}

record Preset(String key, String name, JsonNode config) {}

@Service
@Transactional
class InvoiceTemplateService {
    private final ObjectMapper json;

    @PersistenceContext
    EntityManager em;

    InvoiceTemplateService(ObjectMapper json) {
        this.json = json;
    }

    List<Preset> presets() {
        return List.of("Professional", "Modern", "Minimal", "GST Standard", "Retail", "Wholesale").stream()
                .map(this::preset)
                .toList();
    }

    InvoiceTemplate create(TemplateRequest r) {
        if (r.configJson() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "configJson is required");
        if (!em.createQuery(
                        "from InvoiceTemplate t where t.organizationId=:org and t.templateName=:name",
                        InvoiceTemplate.class)
                .setParameter("org", TenantContext.getOrganizationId())
                .setParameter("name", r.templateName())
                .getResultList()
                .isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Template name already exists");
        InvoiceTemplate t = new InvoiceTemplate();
        t.setOrganizationId(TenantContext.getOrganizationId());
        t.setTemplateName(r.templateName());
        t.setPresetKey(r.presetKey());
        t.setConfigJson(r.configJson());
        em.persist(t);
        return t;
    }

    InvoiceTemplate update(UUID id, TemplateRequest r) {
        InvoiceTemplate t = get(id);
        t.setTemplateName(r.templateName());
        t.setPresetKey(r.presetKey());
        if (r.configJson() != null) t.setConfigJson(r.configJson());
        return t;
    }

    List<InvoiceTemplate> list() {
        return em.createQuery(
                        "from InvoiceTemplate t where t.organizationId=:org order by t.isDefault desc,t.templateName",
                        InvoiceTemplate.class)
                .setParameter("org", TenantContext.getOrganizationId())
                .getResultList();
    }

    InvoiceTemplate setDefault(UUID id) {
        InvoiceTemplate t = get(id);
        em.createQuery("update InvoiceTemplate t set t.isDefault=false where t.organizationId=:org")
                .setParameter("org", TenantContext.getOrganizationId())
                .executeUpdate();
        t.setDefault(true);
        return t;
    }

    void delete(UUID id) {
        em.remove(get(id));
    }

    InvoiceTemplate get(UUID id) {
        InvoiceTemplate t = em.find(InvoiceTemplate.class, id);
        if (t == null || !t.getOrganizationId().equals(TenantContext.getOrganizationId()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Template not found");
        return t;
    }

    private Preset preset(String name) {
        String key = name.toLowerCase(Locale.ROOT).replace(" ", "-");
        return new Preset(
                key,
                name,
                json.valueToTree(Map.of(
                        "logo",
                        Map.of("visible", true, "position", "left"),
                        "header",
                        Map.of("title", "TAX INVOICE", "accentColor", "#1F4E78", "showGstin", true),
                        "items",
                        Map.of(
                                "columns",
                                List.of("#", "Description", "HSN/SAC", "Qty", "Rate", "GST", "Amount"),
                                "showHsn",
                                true,
                                "showTax",
                                true),
                        "footer",
                        Map.of(
                                "showBankDetails",
                                true,
                                "showTerms",
                                true,
                                "showSignature",
                                true,
                                "note",
                                "This is a computer-generated invoice."))));
    }
}

@RestController
@RequestMapping("/api/v1/templates")
class InvoiceTemplateController {
    private final InvoiceTemplateService service;

    InvoiceTemplateController(InvoiceTemplateService s) {
        service = s;
    }

    @GetMapping("/presets")
    List<Preset> presets() {
        return service.presets();
    }

    @GetMapping
    List<InvoiceTemplate> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    InvoiceTemplate create(@RequestBody TemplateRequest r) {
        return service.create(r);
    }

    @GetMapping("/{id}")
    InvoiceTemplate get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    InvoiceTemplate update(@PathVariable UUID id, @RequestBody TemplateRequest r) {
        return service.update(id, r);
    }

    @PostMapping("/{id}/default")
    InvoiceTemplate defaultTemplate(@PathVariable UUID id) {
        return service.setDefault(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
