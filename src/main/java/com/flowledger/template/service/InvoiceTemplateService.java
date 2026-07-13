package com.flowledger.template.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowledger.common.tenant.TenantContext;
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
        return List.of(
                preset("Professional", "SALES_INVOICE", "TAX INVOICE", "#1F4E78", true, true, true, true),
                preset("Modern", "SALES_INVOICE", "INVOICE", "#0F766E", true, true, true, false),
                preset("Minimal", "SALES_INVOICE", "INVOICE", "#334155", false, false, false, false),
                preset("GST Standard", "SALES_INVOICE", "TAX INVOICE", "#1F4E78", true, true, true, true),
                preset("Retail", "SALES_INVOICE", "TAX INVOICE", "#B45309", true, false, true, false),
                preset("Wholesale", "SALES_INVOICE", "TAX INVOICE", "#1E3A8A", true, true, true, true),
                preset("Quotation", "QUOTATION", "QUOTATION", "#0F766E", true, true, false, true),
                preset("Purchase Order", "PURCHASE_ORDER", "PURCHASE ORDER", "#334155", true, true, false, true));
    }

    public InvoiceTemplate create(TemplateRequest r) {
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
        t.setDocumentType(normalizeType(r.documentType()));
        t.setConfigJson(r.configJson());
        em.persist(t);
        return t;
    }

    public InvoiceTemplate update(UUID id, TemplateRequest r) {
        InvoiceTemplate t = get(id);
        t.setTemplateName(r.templateName());
        t.setPresetKey(r.presetKey());
        if (r.documentType() != null && !r.documentType().isBlank()) {
            t.setDocumentType(normalizeType(r.documentType()));
        }
        if (r.configJson() != null) t.setConfigJson(r.configJson());
        return t;
    }

    public List<InvoiceTemplate> list(String documentType) {
        if (documentType == null || documentType.isBlank()) {
            return em.createQuery(
                            "from InvoiceTemplate t where t.organizationId=:org order by t.isDefault desc,t.templateName",
                            InvoiceTemplate.class)
                    .setParameter("org", TenantContext.getOrganizationId())
                    .getResultList();
        }
        return em.createQuery(
                        """
                        from InvoiceTemplate t
                        where t.organizationId=:org and t.documentType=:type
                        order by t.isDefault desc, t.templateName
                        """,
                        InvoiceTemplate.class)
                .setParameter("org", TenantContext.getOrganizationId())
                .setParameter("type", normalizeType(documentType))
                .getResultList();
    }

    public InvoiceTemplate setDefault(UUID id) {
        InvoiceTemplate t = get(id);
        em.createQuery(
                        """
                        update InvoiceTemplate t set t.isDefault=false
                        where t.organizationId=:org and t.documentType=:type
                        """)
                .setParameter("org", TenantContext.getOrganizationId())
                .setParameter("type", t.getDocumentType())
                .executeUpdate();
        t.setDefault(true);
        return t;
    }

    public void delete(UUID id) {
        em.remove(get(id));
    }

    public InvoiceTemplate get(UUID id) {
        InvoiceTemplate t = em.find(InvoiceTemplate.class, id);
        if (t == null || !t.getOrganizationId().equals(TenantContext.getOrganizationId()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Template not found");
        return t;
    }

    public byte[] preview(TemplatePreviewRequest r) {
        if (r == null || r.configJson() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "configJson is required");
        }
        return pdf.renderWithConfig(r.configJson(), normalizeType(r.documentType()), r.sampleInvoiceId());
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
        return new Preset(
                key,
                name,
                documentType,
                json.valueToTree(Map.of(
                        "logo",
                        Map.of("visible", logo, "position", "left"),
                        "header",
                        Map.of("title", title, "accentColor", accent, "showGstin", true),
                        "items",
                        Map.of(
                                "columns",
                                hsn
                                        ? List.of("#", "Description", "HSN/SAC", "Qty", "Rate", "Amount")
                                        : List.of("#", "Description", "Qty", "Rate", "Amount"),
                                "showHsn",
                                hsn,
                                "showTax",
                                true),
                        "footer",
                        Map.of(
                                "showBankDetails",
                                bank,
                                "showTerms",
                                terms,
                                "showSignature",
                                true,
                                "note",
                                "This is a computer-generated document."))));
    }

    private static String normalizeType(String documentType) {
        if (documentType == null || documentType.isBlank()) return "SALES_INVOICE";
        return documentType.trim().toUpperCase(Locale.ROOT);
    }
}
