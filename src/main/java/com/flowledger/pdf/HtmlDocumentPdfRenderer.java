package com.flowledger.pdf;

import com.flowledger.common.util.MergeTags;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Document.OutputSettings;
import org.jsoup.nodes.Entities;
import org.springframework.stereotype.Service;

@Service
public class HtmlDocumentPdfRenderer {
    public byte[] render(String html, Map<String, String> mergeTags) {
        String merged = MergeTags.apply(html == null ? "" : html, mergeTags == null ? Map.of() : mergeTags);
        String document = toXhtml(merged);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(document, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Unable to render HTML PDF: "
                            + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()),
                    ex);
        }
    }

    /**
     * Unlayer exports HTML5 (e.g. {@code <meta>}). OpenHTMLToPDF requires well-formed XHTML.
     */
    static String toXhtml(String html) {
        Document doc = Jsoup.parse(html == null ? "" : html);
        doc.outputSettings()
                .syntax(OutputSettings.Syntax.xml)
                .escapeMode(Entities.EscapeMode.xhtml)
                .prettyPrint(false)
                .charset("UTF-8");

        if (doc.head() != null) {
            if (doc.head().selectFirst("meta[charset]") == null) {
                doc.head().prependElement("meta").attr("charset", "UTF-8");
            }
            if (doc.head().selectFirst("style#flowledger-pdf-base") == null) {
                doc.head()
                        .appendElement("style")
                        .attr("id", "flowledger-pdf-base")
                        .text(
                                """
                                body { font-family: Helvetica, Arial, sans-serif; font-size: 12px; color: #0f172a; }
                                table { width: 100%; border-collapse: collapse; }
                                img { max-width: 100%; }
                                """);
            }
        }

        // Remove scripts / event handlers that OpenHTMLToPDF cannot execute and may break XML.
        doc.select("script").remove();
        doc.select("*").forEach(el -> el.attributes().asList().stream()
                .filter(attr -> attr.getKey().startsWith("on"))
                .forEach(attr -> el.removeAttr(attr.getKey())));

        String xhtml = doc.html();
        if (!xhtml.trim().toLowerCase().startsWith("<?xml")) {
            xhtml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + xhtml;
        }
        return xhtml;
    }
}
