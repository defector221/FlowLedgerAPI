package com.flowledger.labels.service;

import com.flowledger.pdf.HtmlDocumentPdfRenderer;
import com.flowledger.retail.entity.RetailLabelTemplate;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class LabelPdfRenderer {
    private final HtmlDocumentPdfRenderer htmlRenderer;

    public LabelPdfRenderer(HtmlDocumentPdfRenderer htmlRenderer) {
        this.htmlRenderer = htmlRenderer;
    }

    public byte[] render(RetailLabelTemplate template, Map<String, String> values) {
        String html = wrap(template.getTemplateBody(), template.getPaperSize());
        return htmlRenderer.render(html, values);
    }

    private String wrap(String body, String paperSize) {
        String size = paperSize == null || paperSize.isBlank() ? "50x25mm" : paperSize;
        return """
                <!DOCTYPE html>
                <html>
                <head>
                  <meta charset="UTF-8"/>
                  <style>
                    @page { size: %s; margin: 2mm; }
                    body { font-family: Helvetica, Arial, sans-serif; font-size: 10pt; }
                  </style>
                </head>
                <body>%s</body>
                </html>
                """
                .formatted(size, body == null ? "" : body);
    }
}
