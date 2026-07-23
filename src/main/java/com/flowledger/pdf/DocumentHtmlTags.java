package com.flowledger.pdf;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class DocumentHtmlTags {
    private DocumentHtmlTags() {}

    static Map<String, String> sample(OrganizationLogo logo, String organizationName, String organizationGstin) {
        return sample(logo, organizationName, organizationGstin, "INR");
    }

    static Map<String, String> sample(
            OrganizationLogo logo, String organizationName, String organizationGstin, String currencyCode) {
        String currencyPrefix = pdfCurrencyPrefix(currencyCode);
        Map<String, String> tags = new HashMap<>();
        tags.put("organizationName", nullToEmpty(organizationName));
        tags.put("gstin", nullToEmpty(organizationGstin));
        tags.put("organizationEmail", "billing@example.com");
        tags.put("organizationPhone", "+91 98765 00000");
        tags.put("logoHtml", logoHtml(logo));
        tags.put("invoiceNumber", "INV-2026-001");
        tags.put("invoiceDate", "13-07-2026");
        tags.put("documentTitle", "TAX INVOICE");
        tags.put("customerName", "Acme Traders Pvt Ltd");
        tags.put("company", "Acme Traders Pvt Ltd");
        tags.put("customerCode", "CUST-001");
        tags.put("customerEmail", "accounts@acme.example");
        tags.put("customerPhone", "+91 99887 76655");
        tags.put("customerGstin", "27AAAAA0000A1Z5");
        tags.put("customerPan", "AAAAA0000A");
        tags.put("customerAddress", "12 Industrial Estate, Andheri East");
        tags.put("customerCity", "Mumbai");
        tags.put("customerState", "Maharashtra");
        tags.put("customerStateCode", "27");
        tags.put("customerCountry", "India");
        tags.put(
                "customerDetails",
                customerDetailsHtml(
                        "Acme Traders Pvt Ltd",
                        "Acme Traders Pvt Ltd",
                        "CUST-001",
                        "12 Industrial Estate, Andheri East",
                        "Mumbai",
                        "Maharashtra",
                        "27",
                        "India",
                        "27AAAAA0000A1Z5",
                        "AAAAA0000A",
                        "accounts@acme.example",
                        "+91 99887 76655"));
        List<Line> sampleLines = List.of(
                new Line("Industrial motor 5HP", "8501", bd("2"), bd("4500.00"), bd("9000.00"), bd("0"), bd("18")),
                new Line("Mounting kit", "8487", bd("2"), bd("350.00"), bd("665.00"), bd("5"), bd("18")),
                new Line("Installation service", "9987", bd("1"), bd("1500.00"), bd("1500.00"), bd("0"), bd("18")));
        tags.put(
                "lineItemsHtml",
                lineItemsTableHtml(
                        sampleLines,
                        bd("900.00"),
                        bd("900.00"),
                        bd("0.00"),
                        bd("11165.00"),
                        bd("35.00"),
                        bd("11800.00"),
                        true,
                        currencyPrefix));
        tags.put(
                "lineItemsHtmlIvonne",
                lineItemsTableHtmlIvonne(
                        sampleLines,
                        bd("900.00"),
                        bd("900.00"),
                        bd("0.00"),
                        bd("11165.00"),
                        bd("35.00"),
                        bd("11800.00"),
                        currencyPrefix));
        tags.put(
                "currency",
                currencyCode == null || currencyCode.isBlank()
                        ? "INR"
                        : currencyCode.trim().toUpperCase(Locale.ROOT));
        tags.put("currencyPrefix", currencyPrefix);
        tags.put("cgstTotal", "900.00");
        tags.put("sgstTotal", "900.00");
        tags.put("igstTotal", "0.00");
        tags.put("subtotal", "11,165.00");
        tags.put("discountTotal", "35.00");
        tags.put("amountPaid", "0.00");
        tags.put("outstandingAmount", "11,800.00");
        tags.put("amountDueLabel", "Amount due");
        tags.put("grandTotal", "11,800.00");
        tags.put("organizationAddress", "237 Business Park, Andheri East, Mumbai, Maharashtra, India");
        tags.put("notes", "Goods once sold will not be taken back.");
        tags.put("terms", "Payment due within 15 days of invoice date.");
        return tags;
    }

    static String logoHtml(OrganizationLogo logo) {
        if (logo == null || logo.bytes() == null || logo.bytes().length == 0) {
            return "";
        }
        String mime = logo.mimeType() == null ? "image/png" : logo.mimeType();
        String b64 = Base64.getEncoder().encodeToString(logo.bytes());
        return "<img src=\"data:" + mime + ";base64," + b64
                + "\" alt=\"Logo\" style=\"max-height:72px;max-width:200px;display:block;\"/>";
    }

    static String customerDetailsHtml(
            String customerName,
            String companyName,
            String customerCode,
            String address,
            String city,
            String state,
            String stateCode,
            String country,
            String gstin,
            String pan,
            String email,
            String phone) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"font-size:13px;line-height:1.55;color:#0f172a;\">");
        sb.append(
                "<p style=\"margin:0 0 2px;font-size:11px;letter-spacing:0.08em;text-transform:uppercase;color:#94a3b8;\">Bill to</p>");
        sb.append("<p style=\"margin:0;font-size:15px;font-weight:700;\">")
                .append(esc(firstNonBlank(customerName, companyName)))
                .append("</p>");
        if (notBlank(companyName) && !companyName.equalsIgnoreCase(customerName)) {
            sb.append("<p style=\"margin:2px 0 0;\">").append(esc(companyName)).append("</p>");
        }
        if (notBlank(customerCode)) {
            sb.append("<p style=\"margin:2px 0 0;color:#64748b;\">Code: ")
                    .append(esc(customerCode))
                    .append("</p>");
        }
        String location = joinNonBlank(", ", city, state, stateCode, country);
        if (notBlank(address) || notBlank(location)) {
            sb.append("<p style=\"margin:8px 0 0;\">");
            if (notBlank(address)) {
                sb.append(esc(address).replace("\n", "<br/>"));
            }
            if (notBlank(location)) {
                if (notBlank(address)) {
                    sb.append("<br/>");
                }
                sb.append(esc(location));
            }
            sb.append("</p>");
        }
        sb.append("<p style=\"margin:8px 0 0;\">");
        if (notBlank(gstin)) {
            sb.append("<strong>GSTIN:</strong> ").append(esc(gstin));
        }
        if (notBlank(pan)) {
            if (notBlank(gstin)) {
                sb.append("<br/>");
            }
            sb.append("<strong>PAN:</strong> ").append(esc(pan));
        }
        if (notBlank(email) || notBlank(phone)) {
            if (notBlank(gstin) || notBlank(pan)) {
                sb.append("<br/>");
            }
            if (notBlank(email)) {
                sb.append("<strong>Email:</strong> ").append(esc(email));
            }
            if (notBlank(phone)) {
                if (notBlank(email)) {
                    sb.append("<br/>");
                }
                sb.append("<strong>Phone:</strong> ").append(esc(phone));
            }
        }
        sb.append("</p></div>");
        return sb.toString();
    }

    static String lineItemsTableHtml(
            List<Line> lines,
            BigDecimal cgst,
            BigDecimal sgst,
            BigDecimal igst,
            BigDecimal grandTotal,
            boolean showHsn) {
        return lineItemsTableHtml(lines, cgst, sgst, igst, null, null, grandTotal, showHsn);
    }

    static String lineItemsTableHtml(
            List<Line> lines,
            BigDecimal cgst,
            BigDecimal sgst,
            BigDecimal igst,
            BigDecimal subtotal,
            BigDecimal discountTotal,
            BigDecimal grandTotal,
            boolean showHsn) {
        return lineItemsTableHtml(lines, cgst, sgst, igst, subtotal, discountTotal, grandTotal, showHsn, "INR ");
    }

    static String lineItemsTableHtml(
            List<Line> lines,
            BigDecimal cgst,
            BigDecimal sgst,
            BigDecimal igst,
            BigDecimal subtotal,
            BigDecimal discountTotal,
            BigDecimal grandTotal,
            boolean showHsn,
            String currencyPrefix) {
        String cur = currencyPrefix == null || currencyPrefix.isBlank() ? "INR " : currencyPrefix;
        int cols = showHsn ? 8 : 7;
        int labelCols = cols - 1;
        StringBuilder sb = new StringBuilder();
        sb.append("<table width=\"100%\" style=\"border-collapse:collapse;font-size:12px;width:100%;\">");
        sb.append("<thead><tr style=\"background:#334155;color:#ffffff;\">");
        sb.append("<th align=\"left\" style=\"padding:10px 8px;font-weight:600;\">#</th>");
        sb.append("<th align=\"left\" style=\"padding:10px 8px;font-weight:600;\">Description</th>");
        if (showHsn) {
            sb.append("<th align=\"left\" style=\"padding:10px 8px;font-weight:600;\">HSN/SAC</th>");
        }
        sb.append("<th align=\"right\" style=\"padding:10px 8px;font-weight:600;\">Qty</th>");
        sb.append("<th align=\"right\" style=\"padding:10px 8px;font-weight:600;\">Rate</th>");
        sb.append("<th align=\"right\" style=\"padding:10px 8px;font-weight:600;\">Disc %</th>");
        sb.append("<th align=\"right\" style=\"padding:10px 8px;font-weight:600;\">Tax %</th>");
        sb.append("<th align=\"right\" style=\"padding:10px 8px;font-weight:600;\">Amount</th>");
        sb.append("</tr></thead><tbody>");
        if (lines == null || lines.isEmpty()) {
            sb.append("<tr><td colspan=\"")
                    .append(cols)
                    .append("\" style=\"padding:12px;color:#64748b;\">No line items</td></tr>");
        } else {
            int i = 1;
            for (Line line : lines) {
                sb.append("<tr style=\"border-bottom:1px solid #e2e8f0;\">");
                sb.append("<td style=\"padding:8px;color:#64748b;\">")
                        .append(i++)
                        .append("</td>");
                sb.append("<td style=\"padding:8px;\">")
                        .append(esc(line.description()))
                        .append("</td>");
                if (showHsn) {
                    sb.append("<td style=\"padding:8px;\">")
                            .append(esc(nullToEmpty(line.hsn())))
                            .append("</td>");
                }
                sb.append("<td align=\"right\" style=\"padding:8px 10px;\">")
                        .append(esc(fmtQty(line.quantity())))
                        .append("</td>");
                sb.append("<td align=\"right\" style=\"padding:8px 10px;\">")
                        .append(esc(money(cur, line.rate())))
                        .append("</td>");
                sb.append("<td align=\"right\" style=\"padding:8px 10px;\">")
                        .append(esc(fmtPct(line.discountPercent())))
                        .append("</td>");
                sb.append("<td align=\"right\" style=\"padding:8px 10px;\">")
                        .append(esc(fmtPct(line.taxRate())))
                        .append("</td>");
                sb.append("<td align=\"right\" style=\"padding:8px 10px;font-weight:600;\">")
                        .append(esc(money(cur, line.amount())))
                        .append("</td>");
                sb.append("</tr>");
            }
        }
        sb.append("</tbody><tfoot>");
        appendTotalRow(sb, "Subtotal", subtotal, labelCols, cur);
        appendTotalRow(sb, "Discount", discountTotal, labelCols, cur);
        appendTotalRow(sb, "CGST", cgst, labelCols, cur);
        appendTotalRow(sb, "SGST", sgst, labelCols, cur);
        appendTotalRow(sb, "IGST", igst, labelCols, cur);
        sb.append("<tr>");
        sb.append("<td colspan=\"")
                .append(labelCols)
                .append("\" align=\"right\" style=\"padding:10px 8px;font-weight:700;\">Grand total</td>");
        sb.append("<td align=\"right\" style=\"padding:10px 8px;font-weight:700;color:#0f766e;\">")
                .append(esc(money(cur, grandTotal)))
                .append("</td></tr>");
        sb.append("</tfoot></table>");
        return sb.toString();
    }

    /** Light Ivonne-style table: Items / Price / Tax % / Disc % / Total */
    static String lineItemsTableHtmlIvonne(
            List<Line> lines,
            BigDecimal cgst,
            BigDecimal sgst,
            BigDecimal igst,
            BigDecimal subtotal,
            BigDecimal discountTotal,
            BigDecimal grandTotal) {
        return lineItemsTableHtmlIvonne(lines, cgst, sgst, igst, subtotal, discountTotal, grandTotal, "INR ");
    }

    static String lineItemsTableHtmlIvonne(
            List<Line> lines,
            BigDecimal cgst,
            BigDecimal sgst,
            BigDecimal igst,
            BigDecimal subtotal,
            BigDecimal discountTotal,
            BigDecimal grandTotal,
            String currencyPrefix) {
        String cur = currencyPrefix == null || currencyPrefix.isBlank() ? "INR " : currencyPrefix;
        StringBuilder sb = new StringBuilder();
        sb.append(
                "<table width=\"100%\" style=\"border-collapse:collapse;font-size:13px;width:100%;font-family:Helvetica,Arial,sans-serif;\">");
        sb.append("<thead><tr style=\"background:#F3F4F6;color:#6B7280;\">");
        sb.append(
                "<th align=\"left\" style=\"padding:12px 10px;font-weight:600;font-size:11px;letter-spacing:0.04em;text-transform:uppercase;\">Items Details</th>");
        sb.append(
                "<th align=\"right\" style=\"padding:12px 10px;font-weight:600;font-size:11px;letter-spacing:0.04em;text-transform:uppercase;\">Price</th>");
        sb.append(
                "<th align=\"right\" style=\"padding:12px 10px;font-weight:600;font-size:11px;letter-spacing:0.04em;text-transform:uppercase;\">Tax %</th>");
        sb.append(
                "<th align=\"right\" style=\"padding:12px 10px;font-weight:600;font-size:11px;letter-spacing:0.04em;text-transform:uppercase;\">Disc %</th>");
        sb.append(
                "<th align=\"right\" style=\"padding:12px 10px;font-weight:600;font-size:11px;letter-spacing:0.04em;text-transform:uppercase;\">Total</th>");
        sb.append("</tr></thead><tbody>");
        if (lines == null || lines.isEmpty()) {
            sb.append("<tr><td colspan=\"5\" style=\"padding:14px;color:#9CA3AF;\">No line items</td></tr>");
        } else {
            for (Line line : lines) {
                sb.append("<tr style=\"border-bottom:1px solid #E5E7EB;\">");
                sb.append("<td style=\"padding:14px 10px;color:#111827;\">");
                sb.append("<div style=\"font-weight:600;\">")
                        .append(esc(line.description()))
                        .append("</div>");
                if (notBlank(line.hsn())) {
                    sb.append("<div style=\"margin-top:2px;font-size:12px;color:#9CA3AF;\">HSN/SAC ")
                            .append(esc(line.hsn()))
                            .append(" · Qty ")
                            .append(esc(fmtQty(line.quantity())))
                            .append("</div>");
                } else {
                    sb.append("<div style=\"margin-top:2px;font-size:12px;color:#9CA3AF;\">Qty ")
                            .append(esc(fmtQty(line.quantity())))
                            .append("</div>");
                }
                sb.append("</td>");
                sb.append("<td align=\"right\" style=\"padding:14px 10px;color:#374151;\">")
                        .append(esc(money(cur, line.rate())))
                        .append("</td>");
                sb.append("<td align=\"right\" style=\"padding:14px 10px;color:#374151;\">")
                        .append(esc(fmtPct(line.taxRate())))
                        .append("%</td>");
                sb.append("<td align=\"right\" style=\"padding:14px 10px;color:#374151;\">")
                        .append(esc(fmtPct(line.discountPercent())))
                        .append("%</td>");
                sb.append("<td align=\"right\" style=\"padding:14px 10px;font-weight:600;color:#111827;\">")
                        .append(esc(money(cur, line.amount())))
                        .append("</td>");
                sb.append("</tr>");
            }
        }
        sb.append("</tbody><tfoot>");
        if (subtotal != null && subtotal.signum() != 0) {
            sb.append("<tr><td colspan=\"4\" align=\"right\" style=\"padding:8px 10px;color:#6B7280;\">Subtotal</td>");
            sb.append("<td align=\"right\" style=\"padding:8px 10px;\">")
                    .append(esc(money(cur, subtotal)))
                    .append("</td></tr>");
        }
        if (discountTotal != null && discountTotal.signum() != 0) {
            sb.append("<tr><td colspan=\"4\" align=\"right\" style=\"padding:8px 10px;color:#6B7280;\">Discount</td>");
            sb.append("<td align=\"right\" style=\"padding:8px 10px;\">- ")
                    .append(esc(money(cur, discountTotal)))
                    .append("</td></tr>");
        }
        appendIvonneTaxRow(sb, "CGST", cgst, cur);
        appendIvonneTaxRow(sb, "SGST", sgst, cur);
        appendIvonneTaxRow(sb, "IGST", igst, cur);
        sb.append(
                "<tr><td colspan=\"4\" align=\"right\" style=\"padding:14px 10px;font-weight:700;font-size:14px;color:#111827;\">Total Amount</td>");
        sb.append("<td align=\"right\" style=\"padding:14px 10px;font-weight:700;font-size:16px;color:#111827;\">")
                .append(esc(money(cur, grandTotal)))
                .append("</td></tr>");
        sb.append("</tfoot></table>");
        return sb.toString();
    }

    private static void appendIvonneTaxRow(StringBuilder sb, String label, BigDecimal amount, String currencyPrefix) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        sb.append("<tr><td colspan=\"4\" align=\"right\" style=\"padding:6px 10px;color:#6B7280;\">")
                .append(label)
                .append("</td>");
        sb.append("<td align=\"right\" style=\"padding:6px 10px;\">")
                .append(esc(money(currencyPrefix, amount)))
                .append("</td></tr>");
    }

    private static void appendTotalRow(
            StringBuilder sb, String label, BigDecimal amount, int labelCols, String currencyPrefix) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        sb.append("<tr>");
        sb.append("<td colspan=\"")
                .append(labelCols)
                .append("\" align=\"right\" style=\"padding:6px 8px;color:#64748b;\">")
                .append(label)
                .append("</td>");
        sb.append("<td align=\"right\" style=\"padding:6px 8px;\">")
                .append(esc(money(currencyPrefix, amount)))
                .append("</td></tr>");
    }

    private static void appendTotalRow(StringBuilder sb, String label, BigDecimal amount, boolean showHsn) {
        appendTotalRow(sb, label, amount, showHsn ? 7 : 6, "INR ");
    }

    static String mimeFromKey(String objectKey) {
        if (objectKey == null) {
            return "image/png";
        }
        String lower = objectKey.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".svg")) {
            return "image/svg+xml";
        }
        return "image/png";
    }

    /**
     * PDF-safe currency label from organization currency code. Helvetica cannot render ₹ / € / £,
     * which often appear as "#" in PDFs — prefer ISO codes or ASCII symbols.
     */
    static String pdfCurrencyPrefix(String currencyCode) {
        String code = currencyCode == null || currencyCode.isBlank()
                ? "INR"
                : currencyCode.trim().toUpperCase(Locale.ROOT);
        return switch (code) {
            case "INR" -> "INR ";
            case "USD" -> "USD ";
            case "EUR" -> "EUR ";
            case "GBP" -> "GBP ";
            case "AED" -> "AED ";
            case "SGD" -> "SGD ";
            case "AUD" -> "AUD ";
            case "CAD" -> "CAD ";
            default -> code + " ";
        };
    }

    static String money(String currencyPrefix, BigDecimal value) {
        return nullToEmpty(currencyPrefix) + fmtMoney(value);
    }

    static String esc(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    static String fmt(BigDecimal value) {
        return fmtMoney(value);
    }

    /** Money and rates: always 2 decimal places with grouping (en-IN friendly). */
    static String fmtMoney(BigDecimal value) {
        if (value == null) {
            return "0.00";
        }
        return String.format(Locale.ROOT, "%,.2f", value.setScale(2, RoundingMode.HALF_UP));
    }

    /** Quantity: whole numbers without decimals; otherwise up to 2 decimals. */
    static String fmtQty(BigDecimal value) {
        if (value == null) {
            return "0";
        }
        BigDecimal normalized = value.stripTrailingZeros();
        if (normalized.scale() <= 0) {
            return normalized.toPlainString();
        }
        return String.format(Locale.ROOT, "%,.2f", value.setScale(2, RoundingMode.HALF_UP));
    }

    static String fmtPct(BigDecimal value) {
        if (value == null) {
            return "0";
        }
        BigDecimal normalized = value.stripTrailingZeros();
        if (normalized.scale() <= 0) {
            return normalized.toPlainString();
        }
        return String.format(Locale.ROOT, "%.2f", value.setScale(2, RoundingMode.HALF_UP));
    }

    static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    static String firstNonBlank(String... values) {
        for (String v : values) {
            if (notBlank(v)) {
                return v;
            }
        }
        return "";
    }

    static String joinNonBlank(String sep, String... values) {
        StringBuilder sb = new StringBuilder();
        for (String v : values) {
            if (!notBlank(v)) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(sep);
            }
            sb.append(v.trim());
        }
        return sb.toString();
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    record Line(
            String description,
            String hsn,
            BigDecimal quantity,
            BigDecimal rate,
            BigDecimal amount,
            BigDecimal discountPercent,
            BigDecimal taxRate) {
        Line(String description, String hsn, BigDecimal quantity, BigDecimal rate, BigDecimal amount) {
            this(description, hsn, quantity, rate, amount, BigDecimal.ZERO, BigDecimal.ZERO);
        }
    }

    record OrganizationLogo(byte[] bytes, String mimeType) {}
}
