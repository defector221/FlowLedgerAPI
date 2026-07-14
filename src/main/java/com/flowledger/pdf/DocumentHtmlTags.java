package com.flowledger.pdf;

import java.math.BigDecimal;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class DocumentHtmlTags {
    private DocumentHtmlTags() {}

    static Map<String, String> sample(OrganizationLogo logo, String organizationName, String organizationGstin) {
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
        tags.put(
                "lineItemsHtml",
                lineItemsTableHtml(
                        List.of(
                                new Line("Industrial motor 5HP", "8501", bd("2"), bd("4500.00"), bd("9000.00")),
                                new Line("Mounting kit", "8487", bd("2"), bd("350.00"), bd("700.00")),
                                new Line("Installation service", "9987", bd("1"), bd("1500.00"), bd("1500.00"))),
                        bd("900.00"),
                        bd("900.00"),
                        bd("0.00"),
                        bd("11800.00"),
                        true));
        tags.put("cgstTotal", "900.00");
        tags.put("sgstTotal", "900.00");
        tags.put("igstTotal", "0.00");
        tags.put("grandTotal", "11,800.00");
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
        StringBuilder sb = new StringBuilder();
        sb.append("<table width=\"100%\" style=\"border-collapse:collapse;font-size:12px;width:100%;\">");
        sb.append("<thead><tr style=\"background:#0f172a;color:#ffffff;\">");
        sb.append("<th align=\"left\" style=\"padding:8px;\">#</th>");
        sb.append("<th align=\"left\" style=\"padding:8px;\">Description</th>");
        if (showHsn) {
            sb.append("<th align=\"left\" style=\"padding:8px;\">HSN/SAC</th>");
        }
        sb.append("<th align=\"right\" style=\"padding:8px;\">Qty</th>");
        sb.append("<th align=\"right\" style=\"padding:8px;\">Rate</th>");
        sb.append("<th align=\"right\" style=\"padding:8px;\">Amount</th>");
        sb.append("</tr></thead><tbody>");
        if (lines == null || lines.isEmpty()) {
            sb.append("<tr><td colspan=\"")
                    .append(showHsn ? 6 : 5)
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
                sb.append("<td align=\"right\" style=\"padding:8px;\">")
                        .append(esc(fmt(line.quantity())))
                        .append("</td>");
                sb.append("<td align=\"right\" style=\"padding:8px;\">")
                        .append(esc(fmt(line.rate())))
                        .append("</td>");
                sb.append("<td align=\"right\" style=\"padding:8px;font-weight:600;\">")
                        .append(esc(fmt(line.amount())))
                        .append("</td>");
                sb.append("</tr>");
            }
        }
        sb.append("</tbody><tfoot>");
        appendTotalRow(sb, "CGST", cgst, showHsn);
        appendTotalRow(sb, "SGST", sgst, showHsn);
        appendTotalRow(sb, "IGST", igst, showHsn);
        sb.append("<tr>");
        sb.append("<td colspan=\"")
                .append(showHsn ? 5 : 4)
                .append("\" align=\"right\" style=\"padding:10px 8px;font-weight:700;\">Grand total</td>");
        sb.append("<td align=\"right\" style=\"padding:10px 8px;font-weight:700;color:#0f766e;\">")
                .append(esc(fmt(grandTotal)))
                .append("</td></tr>");
        sb.append("</tfoot></table>");
        return sb.toString();
    }

    private static void appendTotalRow(StringBuilder sb, String label, BigDecimal amount, boolean showHsn) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        sb.append("<tr>");
        sb.append("<td colspan=\"")
                .append(showHsn ? 5 : 4)
                .append("\" align=\"right\" style=\"padding:6px 8px;color:#64748b;\">")
                .append(label)
                .append("</td>");
        sb.append("<td align=\"right\" style=\"padding:6px 8px;\">")
                .append(esc(fmt(amount)))
                .append("</td></tr>");
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
        if (value == null) {
            return "0.00";
        }
        return value.stripTrailingZeros().scale() <= 0
                ? value.toPlainString()
                : String.format(Locale.ROOT, "%,.2f", value);
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

    record Line(String description, String hsn, BigDecimal quantity, BigDecimal rate, BigDecimal amount) {}

    record OrganizationLogo(byte[] bytes, String mimeType) {}
}
