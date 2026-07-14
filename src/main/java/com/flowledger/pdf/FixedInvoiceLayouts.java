package com.flowledger.pdf;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Built-in fixed invoice HTML layouts (not Unlayer). Content is filled via merge tags
 * from live invoice data. Visual style is controlled by {@code layoutKey}.
 */
public final class FixedInvoiceLayouts {
    private FixedInvoiceLayouts() {}

    public static final String DEFAULT_KEY = "classic-sage";

    public static final Set<String> KEYS = Set.of(
            "classic-sage",
            "botanical",
            "mint-gradient",
            "coral-accent",
            "elegant-classic",
            "ivonne-hosting");

    public static boolean supports(String layoutKey) {
        return layoutKey != null && KEYS.contains(layoutKey.trim().toLowerCase());
    }

    public static String resolveKey(String layoutKey) {
        return supports(layoutKey) ? layoutKey.trim().toLowerCase() : DEFAULT_KEY;
    }

    public static String html(String layoutKey) {
        return switch (resolveKey(layoutKey)) {
            case "botanical" -> botanical();
            case "mint-gradient" -> mintGradient();
            case "coral-accent" -> coralAccent();
            case "elegant-classic" -> elegantClassic();
            case "ivonne-hosting" -> ivonne();
            default -> classicSage();
        };
    }

    /** Preset metadata used to seed org templates. */
    public static Map<String, PresetMeta> presetMeta() {
        Map<String, PresetMeta> map = new LinkedHashMap<>();
        map.put(
                "classic-sage",
                new PresetMeta("Classic Sage", "#9CAF88", "Payment due within 15 days of invoice date."));
        map.put("botanical", new PresetMeta("Botanical Minimal", "#6B7280", "Thank you for your business."));
        map.put("mint-gradient", new PresetMeta("Mint Gradient", "#14B8A6", "Goods once sold will not be taken back."));
        map.put("coral-accent", new PresetMeta("Coral Accent", "#E07A5F", "Payment due within 15 days of invoice date."));
        map.put(
                "elegant-classic",
                new PresetMeta("Elegant Classic", "#111827", "This is a computer-generated tax invoice."));
        map.put(
                "ivonne-hosting",
                new PresetMeta(
                        "Ivonne Hosting",
                        "#0F766E",
                        "Here we can write additional notes for the client to get a better understanding of this invoice."));
        return map;
    }

    public record PresetMeta(String name, String accent, String defaultTerms) {}

    private static String wrap(String body) {
        return "<!DOCTYPE html><html><head><meta charset=\"utf-8\"/>"
                + "<style>"
                // Avoid quoted multi-word font names — OpenHTMLToPDF parses them as Conversion = '"'
                + "body{font-family:Helvetica,Arial,sans-serif;color:#0f172a;margin:0;padding:28px 32px;font-size:12px;line-height:1.45;}"
                + "table{border-collapse:collapse;width:100%;}"
                + "img{max-height:56px;max-width:160px;display:block;}"
                + ".muted{color:#64748b;}"
                + ".label{font-size:10px;letter-spacing:0.1em;text-transform:uppercase;color:#94a3b8;margin:0 0 6px 0;}"
                + ".title-serif{font-family:Georgia,serif;}"
                + ".amount-box{text-align:right;background:#f8fafc;border:1px solid #e2e8f0;border-radius:8px;padding:14px 18px;}"
                + "</style></head><body>"
                + body
                + "</body></html>";
    }

    private static String partiesBlock(String borderColor) {
        return """
                <table style="margin-top:22px;">
                <tr>
                <td width="48%" valign="top" style="padding:14px 16px 14px 0;">
                <p class="label">Bill from</p>
                <div style="font-size:14px;font-weight:700;margin:0;">{{organizationName}}</div>
                <div class="muted" style="margin-top:6px;line-height:1.55;">{{organizationAddress}}<br/>{{organizationEmail}} · {{organizationPhone}}<br/>GSTIN {{gstin}}</div>
                </td>
                <td width="4%"></td>
                <td width="48%" valign="top" style="padding:14px 0 14px 16px;border-left:1px solid __BORDER__;">
                {{customerDetails}}
                </td>
                </tr></table>
                """
                .replace("__BORDER__", borderColor);
    }

    private static String footerTerms(String accent) {
        return """
                <table style="margin-top:22px;">
                <tr>
                <td width="56%" valign="top" style="padding-right:16px;">
                <p class="label">Terms &amp; conditions</p>
                <div class="muted" style="line-height:1.6;">{{terms}}</div>
                <div class="muted" style="margin-top:12px;"><strong style="color:#475569;">Notes:</strong> {{notes}}</div>
                </td>
                <td width="44%" valign="top" style="text-align:right;">
                <div class="amount-box" style="display:inline-block;min-width:170px;">
                <div class="label" style="color:#64748b;">Amount due</div>
                <div style="font-size:22px;font-weight:700;color:__ACCENT__;margin-top:4px;">{{currencyPrefix}}{{grandTotal}}</div>
                </div>
                <div style="margin-top:28px;font-size:12px;color:#94a3b8;">Authorized signature</div>
                <div style="margin-top:10px;margin-left:auto;width:160px;border-bottom:1px solid #cbd5e1;"></div>
                </td>
                </tr></table>
                """
                .replace("__ACCENT__", accent);
    }

    private static String metaRow(String accent) {
        return """
                <table style="margin-top:14px;font-size:12px;color:#64748b;">
                <tr>
                <td valign="top"><span class="label">Invoice no.</span><strong style="color:#0f172a;font-size:13px;">{{invoiceNumber}}</strong></td>
                <td valign="top"><span class="label">Issued</span><strong style="color:#0f172a;font-size:13px;">{{invoiceDate}}</strong></td>
                <td valign="top" style="text-align:right;"><span class="label">Total</span><strong style="color:__ACCENT__;font-size:16px;">{{currencyPrefix}}{{grandTotal}}</strong></td>
                </tr></table>
                """
                .replace("__ACCENT__", accent);
    }

    private static String classicSage() {
        String accent = "#9CAF88";
        return wrap("""
                <table><tr>
                <td valign="top">{{logoHtml}}
                <div style="font-size:14px;font-weight:700;margin-top:8px;">{{organizationName}}</div>
                <div class="muted" style="font-size:11px;margin-top:2px;">GSTIN {{gstin}}</div>
                </td>
                <td valign="top" style="text-align:right;">
                <div style="font-size:34px;font-weight:700;letter-spacing:0.04em;line-height:1;">Invoice</div>
                <div class="muted" style="margin-top:10px;">No. {{invoiceNumber}} · {{invoiceDate}}</div>
                <div style="font-size:18px;font-weight:700;color:#9CAF88;margin-top:8px;">{{currencyPrefix}}{{grandTotal}}</div>
                </td></tr></table>
                """
                + partiesBlock("#e2e8f0")
                + "<div style=\"margin-top:20px;border-top:2px solid "
                + accent
                + ";padding-top:12px;\"><div style=\"background:#F3F6EF;border-radius:4px;overflow:hidden;\">{{lineItemsHtml}}</div></div>"
                + footerTerms(accent));
    }

    private static String botanical() {
        return wrap("""
                <table><tr>
                <td width="14%" valign="top" style="padding-top:8px;font-size:28px;line-height:1;color:#9CA3AF;">*</td>
                <td valign="top">
                {{logoHtml}}
                <div style="font-size:13px;font-weight:600;margin-top:10px;color:#374151;">{{organizationName}}</div>
                <div class="title-serif" style="font-size:32px;font-weight:600;margin-top:16px;color:#1f2937;">Invoice</div>
                """
                + metaRow("#6B7280")
                + """
                </td></tr></table>
                """
                + partiesBlock("#e5e7eb")
                + "<div style=\"margin-top:20px;\">{{lineItemsHtml}}</div>"
                + footerTerms("#6B7280"));
    }

    private static String mintGradient() {
        String accent = "#0F766E";
        return wrap("""
                <div style="background-color:#5EEAD4;padding:20px 18px;border-radius:6px 6px 0 0;">
                <table><tr>
                <td valign="middle">{{logoHtml}}
                <div style="font-size:13px;font-weight:700;margin-top:6px;color:#0f172a;">{{organizationName}}</div></td>
                <td valign="middle" style="text-align:right;font-size:34px;font-weight:800;color:#0f172a;line-height:1;">Invoice</td>
                </tr></table></div>
                """
                + metaRow(accent)
                + partiesBlock("#e2e8f0")
                + "<div style=\"margin-top:20px;background:#ECFEFF;border-radius:4px;overflow:hidden;\">{{lineItemsHtml}}</div>"
                + footerTerms(accent));
    }

    private static String coralAccent() {
        String accent = "#E07A5F";
        return wrap("""
                <table><tr>
                <td valign="top">{{logoHtml}}
                <div style="font-size:14px;font-weight:700;margin-top:8px;">{{organizationName}}</div>
                <div class="muted" style="font-size:11px;margin-top:2px;">GSTIN {{gstin}}</div>
                </td>
                <td valign="top" style="text-align:right;">
                <div class="title-serif" style="font-size:36px;font-weight:700;color:#E07A5F;text-transform:uppercase;letter-spacing:0.02em;line-height:1;">Invoice</div>
                <div style="color:#9A3412;margin-top:10px;">{{invoiceNumber}} · {{invoiceDate}}</div>
                <div style="font-size:18px;font-weight:700;color:#E07A5F;margin-top:8px;">{{currencyPrefix}}{{grandTotal}}</div>
                </td></tr></table>
                """
                + partiesBlock("#fed7aa")
                + "<div style=\"margin-top:20px;border-top:2px solid "
                + accent
                + ";padding-top:12px;\"><div style=\"background:#FFF7F5;border-radius:4px;overflow:hidden;\">{{lineItemsHtml}}</div></div>"
                + footerTerms(accent));
    }

    private static String elegantClassic() {
        return wrap("""
                <div style="text-align:center;margin-bottom:8px;">
                <div>{{logoHtml}}</div>
                <div class="title-serif" style="font-size:40px;font-weight:500;margin-top:12px;letter-spacing:0.02em;">Invoice</div>
                <div class="muted" style="margin-top:8px;">{{organizationName}} · {{invoiceNumber}} · {{invoiceDate}}</div>
                </div>
                """
                + partiesBlock("#e7e5e4")
                + "<div style=\"margin-top:22px;\">{{lineItemsHtml}}</div>"
                + footerTerms("#111827"));
    }

    private static String ivonne() {
        return wrap("""
                <table><tr>
                <td valign="top"><div style="font-size:22px;font-weight:700;">#{{invoiceNumber}}</div></td>
                <td valign="top" style="text-align:right;">{{logoHtml}}
                <div style="font-size:11px;font-weight:700;letter-spacing:0.12em;text-transform:uppercase;color:#0F766E;margin-top:6px;">{{organizationName}}</div></td>
                </tr></table>
                <table style="margin-top:18px;"><tr>
                <td width="50%" valign="top" style="padding-right:12px;">
                <div style="font-size:12px;font-weight:700;">Invoice / Receipt</div>
                <div class="muted" style="line-height:1.75;margin-top:8px;">
                <strong style="color:#374151;">Invoice:</strong> #{{invoiceNumber}}<br/>
                <strong style="color:#374151;">Customer:</strong> {{customerCode}}<br/>
                <strong style="color:#374151;">Date:</strong> {{invoiceDate}}
                </div></td>
                <td width="50%" valign="top" style="text-align:right;padding-left:12px;">
                <div style="font-size:14px;font-weight:700;">{{organizationName}}</div>
                <div class="muted" style="line-height:1.6;margin-top:6px;">{{organizationAddress}}<br/>{{organizationEmail}}<br/>GSTIN {{gstin}}</div>
                </td></tr></table>
                <table style="margin-top:18px;"><tr>
                <td width="48%" valign="top" style="border:1px solid #E5E7EB;border-radius:8px;padding:16px;">
                <div style="font-size:12px;font-weight:700;">Bill To</div>
                <div style="font-size:14px;font-weight:600;margin-top:8px;">{{customerName}}</div>
                <div class="muted" style="line-height:1.55;margin-top:6px;">{{customerAddress}}<br/>{{customerCity}}, {{customerState}} {{customerCountry}}<br/>{{customerPhone}}<br/>{{customerEmail}}</div>
                </td>
                <td width="4%"></td>
                <td width="48%" valign="top" style="border:1px solid #E5E7EB;border-radius:8px;padding:16px;">
                <div style="font-size:12px;font-weight:700;">Payment INFO</div>
                <div style="font-size:14px;font-weight:600;margin-top:8px;">{{customerName}}</div>
                <div class="muted" style="line-height:1.75;margin-top:10px;">
                <strong style="color:#374151;">Paid:</strong> {{currencyPrefix}}{{amountPaid}}<br/>
                <strong style="color:#374151;">Due:</strong> {{currencyPrefix}}{{outstandingAmount}}<br/>
                <strong style="color:#374151;">Total:</strong> {{currencyPrefix}}{{grandTotal}}
                </div></td></tr></table>
                <div style="margin-top:16px;">{{lineItemsHtmlIvonne}}</div>
                <div style="margin-top:18px;padding-top:14px;border-top:1px solid #E5E7EB;">
                <div style="font-size:13px;font-weight:700;">Note:</div>
                <div class="muted" style="margin-top:6px;line-height:1.55;">{{notes}}</div>
                <div class="muted" style="margin-top:10px;"><strong style="color:#6B7280;">Terms:</strong> {{terms}}</div>
                </div>
                """);
    }
}
