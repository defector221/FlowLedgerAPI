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
        String document = sanitizeCss(toXhtml(merged));
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(document, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception ex) {
            String msg = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            throw new IllegalArgumentException(
                    "Unable to render HTML PDF (" + msg
                            + "). Check template CSS for quoted fonts or unsupported styles.",
                    ex);
        }
    }

    /** OpenHTMLToPDF is picky about CSS quotes and 8-digit hex colors. */
    static String sanitizeCss(String html) {
        if (html == null || html.isBlank()) return "";
        // Multi-word font families with quotes → Georgia/Helvetica only
        String out = html.replaceAll(
                "(?i)font-family\\s*:\\s*[^;\"']*['\"][^;\"']*['\"][^;]*;",
                "font-family: Helvetica, Arial, sans-serif;");
        // 8-digit hex (#RRGGBBAA) is not reliably supported
        out = out.replaceAll("(?i)#([0-9a-f]{6})[0-9a-f]{2}\\b", "#$1");
        out = out.replace("font-variant-numeric:tabular-nums;", "");
        return out;
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
