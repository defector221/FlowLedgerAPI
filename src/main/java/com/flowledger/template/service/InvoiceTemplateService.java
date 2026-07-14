package com.flowledger.template.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flowledger.common.exception.BusinessException;
import com.flowledger.common.tenant.TenantContext;
import com.flowledger.pdf.FixedInvoiceLayouts;
import com.flowledger.pdf.InvoicePdfService;
import com.flowledger.template.dto.TemplateDtos.Preset;
import com.flowledger.template.dto.TemplateDtos.TemplatePreviewRequest;
import com.flowledger.template.dto.TemplateDtos.TemplateRequest;
import com.flowledger.template.entity.InvoiceTemplate;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class InvoiceTemplateService {
    private final ObjectMapper json;
    private final InvoicePdfService pdf;

    @PersistenceContext
    EntityManager em;

    public InvoiceTemplateService(ObjectMapper json, InvoicePdfService pdf) {
        this.json = json;
        this.pdf = pdf;
    }

    public List<Preset> presets() {
        List<Preset> presets = new ArrayList<>();
        for (var entry : FixedInvoiceLayouts.presetMeta().entrySet()) {
            var meta = entry.getValue();
            presets.add(fixedPreset(entry.getKey(), meta.name(), meta.accent(), meta.defaultTerms()));
        }
        presets.add(preset("Professional", "SALES_INVOICE", "TAX INVOICE", "#1F4E78", true, true, true, true));
        presets.add(preset("Modern", "SALES_INVOICE", "INVOICE", "#0F766E", true, true, true, false));
        presets.add(preset("Minimal", "SALES_INVOICE", "INVOICE", "#334155", false, false, false, false));
        presets.add(preset("GST Standard", "SALES_INVOICE", "TAX INVOICE", "#1F4E78", true, true, true, true));
        presets.add(preset("Quotation", "QUOTATION", "QUOTATION", "#0F766E", true, true, false, true));
        presets.add(preset("Purchase Order", "PURCHASE_ORDER", "PURCHASE ORDER", "#334155", true, true, false, true));
        return presets;
    }

    public List<InvoiceTemplate> list(String documentType) {
        ensureFixedLayoutTemplates();
        if (documentType == null || documentType.isBlank()) {
            return em.createQuery(
                            """
                            from InvoiceTemplate t
                            where t.organizationId=:org and t.active=true
                            order by t.isDefault desc, t.templateName
                            """,
                            InvoiceTemplate.class)
                    .setParameter("org", TenantContext.getOrganizationId())
                    .getResultList();
        }
        return em.createQuery(
                        """
                        from InvoiceTemplate t
                        where t.organizationId=:org and t.documentType=:type and t.active=true
                        order by t.isDefault desc, t.templateName
                        """,
                        InvoiceTemplate.class)
                .setParameter("org", TenantContext.getOrganizationId())
                .setParameter("type", normalizeType(documentType))
                .getResultList();
    }

    /** Seed/upgrade the six fixed invoice designs for the current organization. */
    public void ensureFixedLayoutTemplates() {
        UUID org = TenantContext.getOrganizationId();
        if (org == null) return;

        for (var entry : FixedInvoiceLayouts.presetMeta().entrySet()) {
            String key = entry.getKey();
            var meta = entry.getValue();
            InvoiceTemplate existing = findActiveByPresetKey(org, key);
            if (existing == null) {
                existing = findActiveByName(org, meta.name());
            }
            if (existing == null) {
                InvoiceTemplate t = new InvoiceTemplate();
                t.setOrganizationId(org);
                t.setTemplateName(meta.name());
                t.setPresetKey(key);
                t.setDocumentType("SALES_INVOICE");
                t.setEditorMode("SECTION");
                t.setActive(true);
                t.setDefault(false);
                t.setConfigJson(fixedConfig(key, meta.accent(), meta.defaultTerms()));
                em.persist(t);
                continue;
            }
            // Re-attach preset key / layout for templates saved earlier under the same display name
            existing.setPresetKey(key);
            existing.setDocumentType("SALES_INVOICE");
            existing.setEditorMode("SECTION");
            existing.setActive(true);
            existing.setConfigJson(mergeLayoutConfig(existing.getConfigJson(), key, meta.accent(), meta.defaultTerms()));
        }

        long defaults = em.createQuery(
                        """
                        select count(t) from InvoiceTemplate t
                        where t.organizationId=:org and t.documentType='SALES_INVOICE'
                          and t.active=true and t.isDefault=true
                        """,
                        Long.class)
                .setParameter("org", org)
                .getSingleResult();
        if (defaults == 0) {
            InvoiceTemplate classic = findActiveByPresetKey(org, "classic-sage");
            if (classic != null) classic.setDefault(true);
        }
        em.flush();
    }

    private InvoiceTemplate findActiveByPresetKey(UUID org, String presetKey) {
        List<InvoiceTemplate> rows = em.createQuery(
                        """
                        from InvoiceTemplate t
                        where t.organizationId=:org and t.presetKey=:key and t.active=true
                        """,
                        InvoiceTemplate.class)
                .setParameter("org", org)
                .setParameter("key", presetKey)
                .setMaxResults(1)
                .getResultList();
        return rows.isEmpty() ? null : rows.get(0);
    }

    private InvoiceTemplate findActiveByName(UUID org, String templateName) {
        List<InvoiceTemplate> rows = em.createQuery(
                        """
                        from InvoiceTemplate t
                        where t.organizationId=:org and lower(t.templateName)=lower(:name) and t.active=true
                        """,
                        InvoiceTemplate.class)
                .setParameter("org", org)
                .setParameter("name", templateName)
                .setMaxResults(1)
                .getResultList();
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Preset fixedPreset(String layoutKey, String name, String accent, String defaultTerms) {
        return new Preset(layoutKey, name, "SALES_INVOICE", fixedConfig(layoutKey, accent, defaultTerms));
    }

    private JsonNode fixedConfig(String layoutKey, String accent, String defaultTerms) {
        ObjectNode root = json.createObjectNode();
        root.put("layoutKey", layoutKey);
        ObjectNode logo = root.putObject("logo");
        logo.put("visible", true);
        logo.put("position", "left");
        ObjectNode header = root.putObject("header");
        header.put("title", "INVOICE");
        header.put("accentColor", accent);
        header.put("showGstin", true);
        ObjectNode items = root.putObject("items");
        items.putArray("columns")
                .add("#")
                .add("Description")
                .add("HSN/SAC")
                .add("Qty")
                .add("Rate")
                .add("Disc %")
                .add("Tax %")
                .add("Amount");
        items.put("showHsn", true);
        items.put("showTax", true);
        ObjectNode footer = root.putObject("footer");
        footer.put("showBankDetails", true);
        footer.put("showTerms", true);
        footer.put("showSignature", true);
        footer.put("defaultTerms", defaultTerms == null ? "" : defaultTerms);
        footer.put(
                "note",
                defaultTerms == null || defaultTerms.isBlank()
                        ? "This is a computer-generated document."
                        : defaultTerms);
        return root;
    }

    private JsonNode mergeLayoutConfig(JsonNode existing, String layoutKey, String accent, String defaultTerms) {
        ObjectNode root = existing != null && existing.isObject()
                ? ((ObjectNode) existing).deepCopy()
                : json.createObjectNode();
        root.put("layoutKey", layoutKey);
        ObjectNode header = root.has("header") && root.get("header").isObject()
                ? (ObjectNode) root.get("header")
                : root.putObject("header");
        if (!header.hasNonNull("accentColor") || header.get("accentColor").asText().isBlank()) {
            header.put("accentColor", accent);
        }
        if (!header.hasNonNull("title") || header.get("title").asText().isBlank()) {
            header.put("title", "INVOICE");
        }
        ObjectNode footer = root.has("footer") && root.get("footer").isObject()
                ? (ObjectNode) root.get("footer")
                : root.putObject("footer");
        if (!footer.hasNonNull("defaultTerms") || footer.get("defaultTerms").asText().isBlank()) {
            String note = footer.hasNonNull("note") ? footer.get("note").asText() : "";
            footer.put(
                    "defaultTerms",
                    note == null || note.isBlank() ? (defaultTerms == null ? "" : defaultTerms) : note);
        }
        return root;
    }

    private Preset preset(
            String name,
            String documentType,
            String title,
            String accent,
            boolean logo,
            boolean hsn,
            boolean bank,
            boolean terms) {
        String key = name.toLowerCase(Locale.ROOT).replace(" ", "-");
        ObjectNode root = json.createObjectNode();
        ObjectNode logoNode = root.putObject("logo");
        logoNode.put("visible", logo);
        logoNode.put("position", "left");
        ObjectNode header = root.putObject("header");
        header.put("title", title);
        header.put("accentColor", accent);
        header.put("showGstin", true);
        ObjectNode items = root.putObject("items");
        var cols = items.putArray("columns");
        cols.add("#").add("Description");
        if (hsn) cols.add("HSN/SAC");
        cols.add("Qty").add("Rate").add("Amount");
        items.put("showHsn", hsn);
        items.put("showTax", true);
        ObjectNode footer = root.putObject("footer");
        footer.put("showBankDetails", bank);
        footer.put("showTerms", terms);
        footer.put("showSignature", true);
        footer.put("note", "This is a computer-generated document.");
        return new Preset(key, name, documentType, root);
    }

    public InvoiceTemplate create(TemplateRequest r) {
        String mode = normalizeMode(r.editorMode());
        validateContent(mode, r);
        if (!em.createQuery(
                        """
                        from InvoiceTemplate t
                        where t.organizationId=:org and lower(t.templateName)=lower(:name) and t.active=true
                        """,
                        InvoiceTemplate.class)
                .setParameter("org", TenantContext.getOrganizationId())
                .setParameter("name", r.templateName())
                .getResultList()
                .isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Template name already exists");
        InvoiceTemplate t = new InvoiceTemplate();
        t.setOrganizationId(TenantContext.getOrganizationId());
        t.setTemplateName(r.templateName());
        t.setPresetKey(resolvePresetKey(r));
        t.setDocumentType(normalizeType(r.documentType()));
        t.setEditorMode(mode);
        t.setActive(true);
        applyContent(t, mode, r);
        em.persist(t);
        return t;
    }

    public InvoiceTemplate update(UUID id, TemplateRequest r) {
        InvoiceTemplate t = getActive(id);
        String mode =
                r.editorMode() == null || r.editorMode().isBlank() ? t.getEditorMode() : normalizeMode(r.editorMode());
        validateContent(mode, r);
        t.setTemplateName(r.templateName());
        String presetKey = resolvePresetKey(r);
        if (presetKey != null) t.setPresetKey(presetKey);
        else if (r.presetKey() != null) t.setPresetKey(r.presetKey());
        if (r.documentType() != null && !r.documentType().isBlank()) {
            t.setDocumentType(normalizeType(r.documentType()));
        }
        t.setEditorMode(mode);
        applyContent(t, mode, r);
        return t;
    }

    public InvoiceTemplate setDefault(UUID id) {
        InvoiceTemplate t = getActive(id);
        em.createQuery(
                        """
                        update InvoiceTemplate t set t.isDefault=false
                        where t.organizationId=:org and t.documentType=:type and t.active=true
                        """)
                .setParameter("org", TenantContext.getOrganizationId())
                .setParameter("type", t.getDocumentType())
                .executeUpdate();
        t.setDefault(true);
        return t;
    }

    public void delete(UUID id) {
        InvoiceTemplate t = getActive(id);
        t.setActive(false);
        t.setDefault(false);
    }

    public InvoiceTemplate get(UUID id) {
        InvoiceTemplate t = em.find(InvoiceTemplate.class, id);
        if (t == null || !t.getOrganizationId().equals(TenantContext.getOrganizationId()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Template not found");
        return t;
    }

    private InvoiceTemplate getActive(UUID id) {
        InvoiceTemplate t = get(id);
        if (!t.isActive()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Template not found");
        return t;
    }

    public byte[] preview(TemplatePreviewRequest r) {
        if (r == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Preview request is required");
        }
        String mode = normalizeMode(r.editorMode());
        if ("UNLAYER".equals(mode)) {
            if (r.html() == null || r.html().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "html is required for UNLAYER preview");
            }
            return pdf.renderPreview(normalizeType(r.documentType()), r.sampleInvoiceId(), null, mode, r.html());
        }
        if (r.configJson() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "configJson is required");
        }
        return pdf.renderPreview(normalizeType(r.documentType()), r.sampleInvoiceId(), r.configJson(), "SECTION", null);
    }

    private void validateContent(String mode, TemplateRequest r) {
        if ("UNLAYER".equals(mode)) {
            if (r.html() == null || r.html().isBlank()) {
                throw new BusinessException("Exported HTML is required for design templates");
            }
        } else if (r.configJson() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "configJson is required");
        }
    }

    private void applyContent(InvoiceTemplate t, String mode, TemplateRequest r) {
        if ("UNLAYER".equals(mode)) {
            t.setDesignJson(r.designJson());
            t.setHtml(r.html());
            if (r.configJson() != null) {
                t.setConfigJson(r.configJson());
            } else if (t.getConfigJson() == null) {
                t.setConfigJson(json.valueToTree(Map.of("header", Map.of("title", "INVOICE"))));
            }
        } else {
            t.setConfigJson(r.configJson());
            if (r.designJson() != null) {
                t.setDesignJson(r.designJson());
            }
            if (r.html() != null) {
                t.setHtml(r.html());
            }
        }
    }

    private String resolvePresetKey(TemplateRequest r) {
        if (r.presetKey() != null && !r.presetKey().isBlank()) return r.presetKey().trim();
        if (r.configJson() != null && r.configJson().hasNonNull("layoutKey")) {
            return r.configJson().get("layoutKey").asText();
        }
        return null;
    }

    private static String normalizeType(String documentType) {
        if (documentType == null || documentType.isBlank()) return "SALES_INVOICE";
        return documentType.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeMode(String editorMode) {
        if (editorMode == null || editorMode.isBlank()) return "SECTION";
        return editorMode.trim().toUpperCase(Locale.ROOT);
    }
}
