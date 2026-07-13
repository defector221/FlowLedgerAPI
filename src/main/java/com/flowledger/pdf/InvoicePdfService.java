package com.flowledger.pdf;

import com.flowledger.common.tenant.TenantContext;
import com.flowledger.common.util.AmountInWords;
import com.flowledger.organization.entity.Organization;
import com.flowledger.organization.repository.OrganizationRepository;
import com.flowledger.storage.StorageService;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.io.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.*;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

@Service
public class InvoicePdfService {
    @PersistenceContext
    private EntityManager em;

    private final OrganizationRepository organizations;
    private final StorageService storage;

    public InvoicePdfService(OrganizationRepository organizations, StorageService storage) {
        this.organizations = organizations;
        this.storage = storage;
    }

    public byte[] render(UUID invoiceId) {
        Organization org =
                organizations.findById(TenantContext.getOrganizationId()).orElseThrow();
        @SuppressWarnings("unchecked")
        java.util.List<Object[]> invoiceRows = em.createNativeQuery(
                        "select invoice_number,invoice_date,grand_total,cgst_total,sgst_total,igst_total,notes from sales_invoices where id=:id and organization_id=:org")
                .setParameter("id", invoiceId)
                .setParameter("org", org.getId())
                .getResultList();
        if (invoiceRows.isEmpty()) {
            throw new IllegalArgumentException("Invoice not found");
        }
        Object[] invoice = invoiceRows.get(0);
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 36, 36, 50, 50);
            PdfWriter writer = PdfWriter.getInstance(doc, bytes);
            writer.setPageEvent(new Footer());
            doc.open();
            if (org.getLogoObjectKey() != null)
                try (InputStream logo = storage.get(org.getLogoObjectKey())) {
                    Image image = Image.getInstance(logo.readAllBytes());
                    image.scaleToFit(100, 50);
                    doc.add(image);
                } catch (Exception ignored) {
                }
            Paragraph title = new Paragraph("TAX INVOICE", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18));
            title.setAlignment(Element.ALIGN_CENTER);
            doc.add(title);
            doc.add(new Paragraph(org.getLegalName() == null ? org.getName() : org.getLegalName()));
            doc.add(new Paragraph("GSTIN: " + Objects.toString(org.getGstin(), "")));
            PdfPTable meta = new PdfPTable(new float[] {1, 1});
            meta.setWidthPercentage(100);
            meta.addCell(cell("Invoice No: " + invoice[0]));
            meta.addCell(cell("Date: " + invoice[1]));
            doc.add(meta);
            PdfPTable table = new PdfPTable(new float[] {1, 4, 2, 2, 2});
            table.setWidthPercentage(100);
            for (String h : java.util.List.of("#", "Description", "Qty", "Rate", "Amount")) table.addCell(header(h));
            @SuppressWarnings("unchecked")
            java.util.List<Object[]> lines = em.createNativeQuery(
                            "select description,quantity,rate,line_total from sales_invoice_items where sales_invoice_id=:id order by line_order")
                    .setParameter("id", invoiceId)
                    .getResultList();
            int n = 1;
            for (Object[] line : lines) {
                table.addCell(cell(String.valueOf(n++)));
                table.addCell(cell(String.valueOf(line[0])));
                table.addCell(cell(String.valueOf(line[1])));
                table.addCell(cell(String.valueOf(line[2])));
                table.addCell(cell(String.valueOf(line[3])));
            }
            doc.add(table);
            BigDecimal total = (BigDecimal) invoice[2];
            doc.add(new Paragraph("CGST: " + invoice[3] + "   SGST: " + invoice[4] + "   IGST: " + invoice[5]));
            doc.add(new Paragraph("Grand Total: INR " + total, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12)));
            doc.add(new Paragraph("Amount in words: " + AmountInWords.inr(total)));
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
            doc.add(new Paragraph("Bank: " + Objects.toString(org.getBankName(), "") + "  A/C: "
                    + Objects.toString(org.getBankAccountNumber(), "") + "  IFSC: "
                    + Objects.toString(org.getBankIfsc(), "")));
            doc.add(new Paragraph("This is a computer-generated invoice."));
            doc.close();
            return bytes.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to render invoice PDF", e);
        }
    }

    private PdfPCell cell(String text) {
        PdfPCell c = new PdfPCell(new Phrase(text));
        c.setPadding(5);
        return c;
    }

    private PdfPCell header(String text) {
        PdfPCell c = cell(text);
        c.setBackgroundColor(new java.awt.Color(31, 78, 120));
        c.setPhrase(new Phrase(text, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, java.awt.Color.WHITE)));
        return c;
    }

    static class Footer extends PdfPageEventHelper {
        public void onEndPage(PdfWriter w, Document d) {
            ColumnText.showTextAligned(
                    w.getDirectContent(),
                    Element.ALIGN_CENTER,
                    new Phrase("Page " + w.getPageNumber()),
                    d.right() / 2,
                    d.bottom() - 20,
                    0);
        }
    }
}

@RestController
@RequestMapping("/api/v1/invoices")
class InvoiceController {
    private final InvoicePdfService pdf;

    InvoiceController(InvoicePdfService pdf) {
        this.pdf = pdf;
    }

    @GetMapping("/{id}/pdf")
    ResponseEntity<byte[]> download(@PathVariable UUID id) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=invoice-" + id + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf.render(id));
    }

    @GetMapping("/{id}/preview")
    ResponseEntity<byte[]> preview(@PathVariable UUID id) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).body(pdf.render(id));
    }
}
