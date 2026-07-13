package com.flowledger.pdf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowledger.common.tenant.TenantContext;
import com.flowledger.common.util.AmountInWords;
import com.flowledger.organization.entity.Organization;
import com.flowledger.organization.repository.OrganizationRepository;
import com.flowledger.storage.StorageService;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.awt.Color;
import java.io.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class InvoicePdfService {
    @PersistenceContext
    private EntityManager em;

    private final OrganizationRepository organizations;
    private final StorageService storage;
    private final ObjectMapper json;

    public InvoicePdfService(OrganizationRepository organizations, StorageService storage, ObjectMapper json) {
        this.organizations = organizations;
        this.storage = storage;
        this.json = json;
    }

    public byte[] render(UUID invoiceId) {
        return renderDocument("SALES_INVOICE", invoiceId);
    }

    public byte[] renderDocument(String type, UUID id) {
        return renderDocument(type, id, null);
    }

    public byte[] renderDocument(String type, UUID id, JsonNode overrideConfig) {
        String docType = type == null ? "SALES_INVOICE" : type.trim().toUpperCase(Locale.ROOT);
        return switch (docType) {
            case "SALES_INVOICE", "INVOICE" -> renderSalesInvoice(id, overrideConfig);
            case "QUOTATION" -> renderQuotation(id, overrideConfig);
            case "PURCHASE_ORDER" -> renderPurchaseOrder(id, overrideConfig);
            default -> throw new IllegalArgumentException("Unsupported document type: " + docType);
        };
    }

    public byte[] renderWithConfig(JsonNode configJson, String documentType, UUID sampleInvoiceId) {
        String type = documentType == null || documentType.isBlank() ? "SALES_INVOICE" : documentType;
        if (sampleInvoiceId != null) {
            return renderDocument(type, sampleInvoiceId, configJson);
        }
        return renderSample(type, configJson);
    }

    private byte[] renderSalesInvoice(UUID invoiceId, JsonNode overrideConfig) {
        Organization org = org();
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                        """
                        select si.invoice_number, si.invoice_date, si.grand_total, si.cgst_total, si.sgst_total, si.igst_total,
                               si.notes, si.terms_and_conditions, si.template_id, si.billing_address, si.customer_gstin,
                               c.customer_name, c.billing_address as customer_billing, c.gstin as customer_gstin_master,
                               c.city, c.state, c.state_code
                        from sales_invoices si
                        join customers c on c.id = si.customer_id
                        where si.id = :id and si.organization_id = :org
                        """)
                .setParameter("id", invoiceId)
                .setParameter("org", org.getId())
                .getResultList();
        if (rows.isEmpty()) throw new IllegalArgumentException("Invoice not found");
        Object[] inv = rows.get(0);
        UUID templateId = inv[8] instanceof UUID u ? u : null;
        TemplateConfig cfg = resolveConfig(overrideConfig, templateId, "SALES_INVOICE");

        @SuppressWarnings("unchecked")
        List<Object[]> lines = em.createNativeQuery(
                        """
                        select description, quantity, rate, line_total, coalesce(hsn_sac_code, '')
                        from sales_invoice_items
                        where sales_invoice_id = :id
                        order by line_order
                        """)
                .setParameter("id", invoiceId)
                .getResultList();

        String customerName = str(inv[11]);
        String billTo = firstNonBlank(str(inv[9]), str(inv[12]));
        String customerGstin = firstNonBlank(str(inv[10]), str(inv[13]));
        String cityState = joinNonBlank(", ", str(inv[14]), str(inv[15]), str(inv[16]));
        if (!cityState.isBlank()) billTo = billTo.isBlank() ? cityState : billTo + "\n" + cityState;

        DocumentModel model = new DocumentModel(
                cfg.titleOr("TAX INVOICE"),
                "Invoice No",
                str(inv[0]),
                "Date",
                str(inv[1]),
                customerName,
                billTo,
                customerGstin,
                lines,
                (BigDecimal) inv[2],
                inv[3],
                inv[4],
                inv[5],
                str(inv[6]),
                str(inv[7]),
                true);
        return buildPdf(org, cfg, model);
    }

    private byte[] renderQuotation(UUID quotationId, JsonNode overrideConfig) {
        Organization org = org();
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                        """
                        select q.quotation_number, q.quotation_date, q.grand_total, q.tax_total, q.notes, q.terms_and_conditions,
                               q.billing_address, c.customer_name, c.billing_address as customer_billing, c.gstin,
                               c.city, c.state, c.state_code
                        from quotations q
                        join customers c on c.id = q.customer_id
                        where q.id = :id and q.organization_id = :org
                        """)
                .setParameter("id", quotationId)
                .setParameter("org", org.getId())
                .getResultList();
        if (rows.isEmpty()) throw new IllegalArgumentException("Quotation not found");
        Object[] q = rows.get(0);
        TemplateConfig cfg = resolveConfig(overrideConfig, null, "QUOTATION");

        @SuppressWarnings("unchecked")
        List<Object[]> lines = em.createNativeQuery(
                        """
                        select description, quantity, rate, line_total, coalesce(hsn_sac_code, '')
                        from quotation_items
                        where quotation_id = :id
                        order by line_order
                        """)
                .setParameter("id", quotationId)
                .getResultList();

        String billTo = firstNonBlank(str(q[6]), str(q[8]));
        String cityState = joinNonBlank(", ", str(q[10]), str(q[11]), str(q[12]));
        if (!cityState.isBlank()) billTo = billTo.isBlank() ? cityState : billTo + "\n" + cityState;

        DocumentModel model = new DocumentModel(
                cfg.titleOr("QUOTATION"),
                "Quotation No",
                str(q[0]),
                "Date",
                str(q[1]),
                str(q[7]),
                billTo,
                str(q[9]),
                lines,
                (BigDecimal) q[2],
                null,
                null,
                q[3],
                str(q[4]),
                str(q[5]),
                false);
        return buildPdf(org, cfg, model);
    }

    private byte[] renderPurchaseOrder(UUID poId, JsonNode overrideConfig) {
        Organization org = org();
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                        """
                        select po.po_number, po.order_date, po.grand_total, po.tax_total, po.notes, po.terms_and_conditions,
                               s.supplier_name, s.billing_address, s.gstin, s.city, s.state, s.state_code
                        from purchase_orders po
                        join suppliers s on s.id = po.supplier_id
                        where po.id = :id and po.organization_id = :org
                        """)
                .setParameter("id", poId)
                .setParameter("org", org.getId())
                .getResultList();
        if (rows.isEmpty()) throw new IllegalArgumentException("Purchase order not found");
        Object[] po = rows.get(0);
        TemplateConfig cfg = resolveConfig(overrideConfig, null, "PURCHASE_ORDER");

        @SuppressWarnings("unchecked")
        List<Object[]> lines = em.createNativeQuery(
                        """
                        select description, quantity, rate, line_total, coalesce(hsn_sac_code, '')
                        from purchase_order_items
                        where purchase_order_id = :id
                        order by line_order
                        """)
                .setParameter("id", poId)
                .getResultList();

        String billTo = str(po[7]);
        String cityState = joinNonBlank(", ", str(po[9]), str(po[10]), str(po[11]));
        if (!cityState.isBlank()) billTo = billTo.isBlank() ? cityState : billTo + "\n" + cityState;

        DocumentModel model = new DocumentModel(
                cfg.titleOr("PURCHASE ORDER"),
                "PO No",
                str(po[0]),
                "Date",
                str(po[1]),
                str(po[6]),
                billTo,
                str(po[8]),
                lines,
                (BigDecimal) po[2],
                null,
                null,
                po[3],
                str(po[4]),
                str(po[5]),
                false);
        return buildPdf(org, cfg, model);
    }

    private byte[] renderSample(String documentType, JsonNode configJson) {
        Organization org = org();
        TemplateConfig cfg = TemplateConfig.from(configJson != null ? configJson : defaultConfig(documentType));
        List<Object[]> lines = List.of(
                new Object[] {"Sample product A", BigDecimal.ONE, new BigDecimal("100.00"), new BigDecimal("100.00"), "9983"},
                new Object[] {"Sample product B", new BigDecimal("2"), new BigDecimal("50.00"), new BigDecimal("100.00"), "9984"});
        DocumentModel model = new DocumentModel(
                cfg.titleOr(defaultTitle(documentType)),
                "Doc No",
                "SAMPLE-001",
                "Date",
                java.time.LocalDate.now().toString(),
                "Sample Customer",
                "123 Sample Street\nMumbai, MH 400001",
                "27AAAAA0000A1Z5",
                lines,
                new BigDecimal("200.00"),
                new BigDecimal("9.00"),
                new BigDecimal("9.00"),
                BigDecimal.ZERO,
                "Sample notes",
                org.getPaymentTerms(),
                true);
        return buildPdf(org, cfg, model);
    }

    private byte[] buildPdf(Organization org, TemplateConfig cfg, DocumentModel model) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 36, 36, 50, 50);
            PdfWriter writer = PdfWriter.getInstance(doc, bytes);
            writer.setPageEvent(new Footer());
            doc.open();

            if (cfg.logoVisible && org.getLogoObjectKey() != null) {
                try (InputStream logo = storage.get(org.getLogoObjectKey())) {
                    Image image = Image.getInstance(logo.readAllBytes());
                    image.scaleToFit(100, 50);
                    doc.add(image);
                } catch (Exception ignored) {
                }
            }

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, cfg.accentColor);
            Paragraph title = new Paragraph(model.title, titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(8);
            doc.add(title);

            doc.add(new Paragraph(org.getLegalName() == null ? org.getName() : org.getLegalName(),
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11)));
            if (org.getBillingAddress() != null && !org.getBillingAddress().isBlank()) {
                doc.add(new Paragraph(org.getBillingAddress(), FontFactory.getFont(FontFactory.HELVETICA, 9)));
            }
            if (cfg.showGstin && org.getGstin() != null && !org.getGstin().isBlank()) {
                doc.add(new Paragraph("GSTIN: " + org.getGstin(), FontFactory.getFont(FontFactory.HELVETICA, 9)));
            }
            doc.add(Chunk.NEWLINE);

            PdfPTable parties = new PdfPTable(new float[] {1, 1});
            parties.setWidthPercentage(100);
            parties.setSpacingAfter(10);
            PdfPCell billTo = new PdfPCell();
            billTo.setBorder(Rectangle.BOX);
            billTo.setPadding(6);
            billTo.addElement(new Paragraph("Bill To", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9)));
            billTo.addElement(new Paragraph(model.partyName, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10)));
            if (model.partyAddress != null && !model.partyAddress.isBlank()) {
                billTo.addElement(new Paragraph(model.partyAddress, FontFactory.getFont(FontFactory.HELVETICA, 9)));
            }
            if (cfg.showGstin && model.partyGstin != null && !model.partyGstin.isBlank()) {
                billTo.addElement(new Paragraph("GSTIN: " + model.partyGstin, FontFactory.getFont(FontFactory.HELVETICA, 9)));
            }
            parties.addCell(billTo);

            PdfPCell meta = new PdfPCell();
            meta.setBorder(Rectangle.BOX);
            meta.setPadding(6);
            meta.addElement(new Paragraph(model.numberLabel + ": " + model.number, FontFactory.getFont(FontFactory.HELVETICA, 9)));
            meta.addElement(new Paragraph(model.dateLabel + ": " + model.date, FontFactory.getFont(FontFactory.HELVETICA, 9)));
            parties.addCell(meta);
            doc.add(parties);

            List<String> headers = new ArrayList<>(List.of("#", "Description"));
            if (cfg.showHsn) headers.add("HSN/SAC");
            headers.addAll(List.of("Qty", "Rate", "Amount"));
            float[] widths = cfg.showHsn ? new float[] {0.6f, 3.2f, 1.2f, 1f, 1.2f, 1.4f} : new float[] {0.6f, 4f, 1f, 1.2f, 1.4f};
            PdfPTable table = new PdfPTable(widths);
            table.setWidthPercentage(100);
            table.setSpacingBefore(6);
            for (String h : headers) table.addCell(header(h, cfg.accentColor));

            int n = 1;
            for (Object[] line : model.lines) {
                table.addCell(cell(String.valueOf(n++)));
                table.addCell(cell(str(line[0])));
                if (cfg.showHsn) table.addCell(cell(str(line[4])));
                table.addCell(cell(str(line[1])));
                table.addCell(cell(str(line[2])));
                table.addCell(cell(str(line[3])));
            }
            doc.add(table);

            doc.add(Chunk.NEWLINE);
            if (model.showGstBreakdown) {
                doc.add(new Paragraph(
                        "CGST: " + Objects.toString(model.cgst, "0") + "   SGST: " + Objects.toString(model.sgst, "0")
                                + "   IGST: " + Objects.toString(model.igst, "0"),
                        FontFactory.getFont(FontFactory.HELVETICA, 9)));
            } else if (model.igst != null) {
                doc.add(new Paragraph("Tax: " + model.igst, FontFactory.getFont(FontFactory.HELVETICA, 9)));
            }
            BigDecimal total = model.grandTotal == null ? BigDecimal.ZERO : model.grandTotal;
            doc.add(new Paragraph(
                    "Grand Total: INR " + total, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, cfg.accentColor)));
            doc.add(new Paragraph("Amount in words: " + AmountInWords.inr(total), FontFactory.getFont(FontFactory.HELVETICA, 9)));

            if (cfg.showBankDetails) {
                doc.add(Chunk.NEWLINE);
                doc.add(new Paragraph("Bank details", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10)));
                doc.add(new Paragraph(
                        "Bank: " + Objects.toString(org.getBankName(), "") + "  A/C: "
                                + Objects.toString(org.getBankAccountNumber(), "") + "  IFSC: "
                                + Objects.toString(org.getBankIfsc(), ""),
                        FontFactory.getFont(FontFactory.HELVETICA, 9)));
                if (org.getUpiId() != null && !org.getUpiId().isBlank()) {
                    File qr = File.createTempFile("flowledger-upi", ".png");
                    MatrixToImageWriter.writeToPath(
                            new QRCodeWriter()
                                    .encode(
                                            "upi://pay?pa=" + org.getUpiId() + "&am=" + total,
                                            BarcodeFormat.QR_CODE,
                                            150,
                                            150),
                            "PNG",
                            qr.toPath());
                    Image image = Image.getInstance(Files.readAllBytes(qr.toPath()));
                    image.scaleToFit(90, 90);
                    doc.add(image);
                    qr.delete();
                }
            }

            if (cfg.showTerms) {
                String terms = firstNonBlank(model.terms, org.getPaymentTerms());
                if (terms != null && !terms.isBlank()) {
                    doc.add(Chunk.NEWLINE);
                    doc.add(new Paragraph("Terms & conditions", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10)));
                    doc.add(new Paragraph(terms, FontFactory.getFont(FontFactory.HELVETICA, 9)));
                }
            }

            if (model.notes != null && !model.notes.isBlank()) {
                doc.add(new Paragraph("Notes: " + model.notes, FontFactory.getFont(FontFactory.HELVETICA, 9)));
            }
            if (cfg.note != null && !cfg.note.isBlank()) {
                doc.add(Chunk.NEWLINE);
                doc.add(new Paragraph(cfg.note, FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8, Color.GRAY)));
            }

            doc.close();
            return bytes.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to render PDF", e);
        }
    }

    private TemplateConfig resolveConfig(JsonNode override, UUID templateId, String documentType) {
        if (override != null && !override.isNull()) return TemplateConfig.from(override);
        UUID orgId = TenantContext.getOrganizationId();
        if (templateId != null) {
            @SuppressWarnings("unchecked")
            List<Object> configs = em.createNativeQuery(
                            "select config_json::text from invoice_templates where id=:id and organization_id=:org")
                    .setParameter("id", templateId)
                    .setParameter("org", orgId)
                    .getResultList();
            if (!configs.isEmpty()) return TemplateConfig.from(parseJson(str(configs.get(0))));
        }
        @SuppressWarnings("unchecked")
        List<Object> defaults = em.createNativeQuery(
                        """
                        select config_json::text from invoice_templates
                        where organization_id = :org and document_type = :type and is_default = true
                        limit 1
                        """)
                .setParameter("org", orgId)
                .setParameter("type", documentType)
                .getResultList();
        if (!defaults.isEmpty()) return TemplateConfig.from(parseJson(str(defaults.get(0))));
        @SuppressWarnings("unchecked")
        List<Object> anyDefault = em.createNativeQuery(
                        """
                        select config_json::text from invoice_templates
                        where organization_id = :org and is_default = true
                        limit 1
                        """)
                .setParameter("org", orgId)
                .getResultList();
        if (!anyDefault.isEmpty()) return TemplateConfig.from(parseJson(str(anyDefault.get(0))));
        return TemplateConfig.from(defaultConfig(documentType));
    }

    private JsonNode defaultConfig(String documentType) {
        return json.valueToTree(Map.of(
                "logo", Map.of("visible", true, "position", "left"),
                "header",
                        Map.of(
                                "title",
                                defaultTitle(documentType),
                                "accentColor",
                                "#1F4E78",
                                "showGstin",
                                true),
                "items",
                        Map.of(
                                "columns",
                                List.of("#", "Description", "HSN/SAC", "Qty", "Rate", "Amount"),
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
                                "This is a computer-generated document.")));
    }

    private String defaultTitle(String documentType) {
        return switch (documentType == null ? "" : documentType.toUpperCase(Locale.ROOT)) {
            case "QUOTATION" -> "QUOTATION";
            case "PURCHASE_ORDER" -> "PURCHASE ORDER";
            default -> "TAX INVOICE";
        };
    }

    private JsonNode parseJson(String raw) {
        try {
            return json.readTree(raw);
        } catch (Exception e) {
            return json.createObjectNode();
        }
    }

    private Organization org() {
        return organizations.findById(TenantContext.getOrganizationId()).orElseThrow();
    }

    private PdfPCell cell(String text) {
        PdfPCell c = new PdfPCell(new Phrase(text == null ? "" : text, FontFactory.getFont(FontFactory.HELVETICA, 9)));
        c.setPadding(5);
        return c;
    }

    private PdfPCell header(String text, Color accent) {
        PdfPCell c = cell(text);
        c.setBackgroundColor(accent);
        c.setPhrase(new Phrase(text, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE)));
        return c;
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return "";
    }

    private static String joinNonBlank(String sep, String... values) {
        StringBuilder sb = new StringBuilder();
        for (String v : values) {
            if (v == null || v.isBlank()) continue;
            if (!sb.isEmpty()) sb.append(sep);
            sb.append(v);
        }
        return sb.toString();
    }

    private record DocumentModel(
            String title,
            String numberLabel,
            String number,
            String dateLabel,
            String date,
            String partyName,
            String partyAddress,
            String partyGstin,
            List<Object[]> lines,
            BigDecimal grandTotal,
            Object cgst,
            Object sgst,
            Object igst,
            String notes,
            String terms,
            boolean showGstBreakdown) {}

    static final class TemplateConfig {
        final boolean logoVisible;
        final String title;
        final Color accentColor;
        final boolean showGstin;
        final boolean showHsn;
        final boolean showBankDetails;
        final boolean showTerms;
        final String note;

        TemplateConfig(
                boolean logoVisible,
                String title,
                Color accentColor,
                boolean showGstin,
                boolean showHsn,
                boolean showBankDetails,
                boolean showTerms,
                String note) {
            this.logoVisible = logoVisible;
            this.title = title;
            this.accentColor = accentColor;
            this.showGstin = showGstin;
            this.showHsn = showHsn;
            this.showBankDetails = showBankDetails;
            this.showTerms = showTerms;
            this.note = note;
        }

        static TemplateConfig from(JsonNode root) {
            JsonNode logo = root.path("logo");
            JsonNode header = root.path("header");
            JsonNode items = root.path("items");
            JsonNode footer = root.path("footer");
            return new TemplateConfig(
                    logo.path("visible").asBoolean(true),
                    text(header, "title", null),
                    parseColor(text(header, "accentColor", "#1F4E78")),
                    header.path("showGstin").asBoolean(true),
                    items.path("showHsn").asBoolean(true),
                    footer.path("showBankDetails").asBoolean(true),
                    footer.path("showTerms").asBoolean(true),
                    text(footer, "note", "This is a computer-generated document."));
        }

        String titleOr(String fallback) {
            return title == null || title.isBlank() ? fallback : title;
        }

        private static String text(JsonNode node, String field, String fallback) {
            JsonNode value = node.path(field);
            if (value.isMissingNode() || value.isNull()) return fallback;
            String s = value.asText();
            return s == null || s.isBlank() ? fallback : s;
        }

        private static Color parseColor(String hex) {
            try {
                String h = hex == null ? "#1F4E78" : hex.trim();
                if (h.startsWith("#")) h = h.substring(1);
                if (h.length() == 3) {
                    h = "" + h.charAt(0) + h.charAt(0) + h.charAt(1) + h.charAt(1) + h.charAt(2) + h.charAt(2);
                }
                return new Color(Integer.parseInt(h, 16));
            } catch (Exception e) {
                return new Color(31, 78, 120);
            }
        }
    }

    static class Footer extends PdfPageEventHelper {
        public void onEndPage(PdfWriter w, Document d) {
            ColumnText.showTextAligned(
                    w.getDirectContent(),
                    Element.ALIGN_CENTER,
                    new Phrase("Page " + w.getPageNumber()),
                    (d.right() + d.left()) / 2,
                    d.bottom() - 20,
                    0);
        }
    }
}
