package com.flowledger.pdf;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class HtmlDocumentPdfRendererTest {
    @Test
    void rendersUnlayerStyleHtml5WithVoidMetaTags() {
        String unlayerLike =
                """
                <!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
                <html>
                <head>
                  <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
                  <meta name="viewport" content="width=device-width">
                  <title>Tax invoice</title>
                </head>
                <body>
                  <h1>Invoice {{invoiceNumber}}</h1>
                  <p>Bill to {{customerName}}</p>
                  <p>Total ₹ {{grandTotal}}</p>
                  <br>
                  <img src="data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7" alt="">
                </body>
                </html>
                """;

        String xhtml = HtmlDocumentPdfRenderer.toXhtml(unlayerLike);
        assertTrue(xhtml.contains("/>") || xhtml.contains("</meta>") || xhtml.contains("meta"));
        assertTrue(!xhtml.contains("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">"));

        HtmlDocumentPdfRenderer renderer = new HtmlDocumentPdfRenderer();
        byte[] pdf = assertDoesNotThrow(() -> renderer.render(
                unlayerLike,
                Map.of(
                        "invoiceNumber", "INV-1",
                        "customerName", "Acme",
                        "grandTotal", "1000.00")));
        assertTrue(pdf.length > 100);
    }
}
