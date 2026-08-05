package com.flowledger.ai.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowledger.ai.config.AiProperties;
import com.flowledger.ai.config.ConditionalOnAiEnabled;
import com.flowledger.ai.dto.AiDtos;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Document OCR / field extraction. Advisory only — never posts or confirms ERP documents.
 */
@Service
@ConditionalOnAiEnabled
public class DocumentAiService {
    private static final Pattern GSTIN = Pattern.compile("\\b\\d{2}[A-Z]{5}\\d{4}[A-Z][A-Z0-9]Z[A-Z0-9]\\b");
    private static final Pattern AMOUNT = Pattern.compile("(?i)(?:total|grand\\s*total|amount)\\s*[:]?\\s*(?:INR|Rs\\.?|₹)?\\s*([0-9,.]+)");

    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public DocumentAiService(
            AiProperties properties, ObjectMapper objectMapper, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder.build();
    }

    public AiDtos.DocumentAiResponse extract(AiDtos.DocumentAiRequest request) {
        if (!properties.isDocumentAiEnabled()) {
            return new AiDtos.DocumentAiResponse(
                    false, "Document AI not configured. Set flowledger.ai.document-ai-enabled=true.", Map.of());
        }
        String filename = request == null || request.filename() == null ? "document" : request.filename();
        String contentBase64 = request == null ? null : request.contentBase64();
        if (contentBase64 == null || contentBase64.isBlank()) {
            return new AiDtos.DocumentAiResponse(true, "No document content provided.", Map.of("draftOnly", true));
        }

        Map<String, Object> draft = properties.hasApiKey()
                ? extractWithVision(filename, contentBase64)
                : extractHeuristically(filename, contentBase64);
        draft.put("draftOnly", true);
        draft.put("filename", filename);
        draft.put("confidence", draft.getOrDefault("confidence", 0.55));
        return new AiDtos.DocumentAiResponse(
                true, "Draft fields extracted. Review and confirm before creating any ERP document.", draft);
    }

    public AiDtos.DocumentDraftConfirmResponse confirmDraft(AiDtos.DocumentDraftConfirmRequest request) {
        if (!properties.isDocumentAiEnabled()) {
            return new AiDtos.DocumentDraftConfirmResponse(
                    false, "Document AI not configured.", null, null);
        }
        if (request == null || request.draftFields() == null || request.draftFields().isEmpty()) {
            return new AiDtos.DocumentDraftConfirmResponse(false, "draftFields are required", null, null);
        }
        Double confidence = asDouble(request.draftFields().get("confidence"));
        if (confidence != null && confidence < 0.4) {
            return new AiDtos.DocumentDraftConfirmResponse(
                    false, "Confidence too low to create a draft. Fix fields and retry.", request.documentType(), null);
        }
        // Explicit human-confirm gate: return a pseudo draft id representing staged payload.
        // ERP create remains a separate Purchase/Sales UI action using these fields.
        UUID stagedId = UUID.nameUUIDFromBytes(
                (TenantSafe.fingerprint(request.draftFields()) + "|" + System.currentTimeMillis())
                        .getBytes());
        return new AiDtos.DocumentDraftConfirmResponse(
                true,
                "Draft accepted for review. Open Purchases/Sales and create the document from these fields — AI will not auto-post.",
                request.documentType() == null ? "PURCHASE_INVOICE" : request.documentType(),
                stagedId);
    }

    private Map<String, Object> extractWithVision(String filename, String contentBase64) {
        try {
            String lower = filename.toLowerCase(Locale.ROOT);
            boolean image = lower.endsWith(".png")
                    || lower.endsWith(".jpg")
                    || lower.endsWith(".jpeg")
                    || lower.endsWith(".webp")
                    || lower.endsWith(".gif");
            List<Map<String, Object>> content = new ArrayList<>();
            content.add(Map.of(
                    "type",
                    "text",
                    "text",
                    """
                    Extract supplier invoice fields as JSON with keys:
                    suggestedDocumentType, vendorName, gstin, invoiceNumber, invoiceDate, currency, totalAmount, lineItems (array of description, quantity, rate, amount), confidence (0-1).
                    Return JSON only.
                    """));
            if (image) {
                String mime = lower.endsWith(".png") ? "image/png" : "image/jpeg";
                content.add(Map.of(
                        "type",
                        "image_url",
                        "image_url",
                        Map.of("url", "data:" + mime + ";base64," + contentBase64)));
            } else {
                String text = new String(Base64.getDecoder().decode(contentBase64));
                content.add(Map.of("type", "text", "text", "Document text:\n" + text.substring(0, Math.min(text.length(), 12000))));
            }
            Map<String, Object> body = new HashMap<>();
            body.put("model", properties.getOpenai().getChatModel());
            body.put("messages", List.of(Map.of("role", "user", "content", content)));
            body.put("temperature", 0);
            String raw = restClient
                    .post()
                    .uri(trimSlash(properties.getOpenai().getBaseUrl()) + "/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.getOpenai().getApiKey())
                    .body(body)
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(raw);
            String message = root.path("choices").path(0).path("message").path("content").asText("");
            String json = extractJsonObject(message);
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(json, Map.class);
            parsed.putIfAbsent("confidence", 0.8);
            return new LinkedHashMap<>(parsed);
        } catch (Exception e) {
            return extractHeuristically(filename, contentBase64);
        }
    }

    private Map<String, Object> extractHeuristically(String filename, String contentBase64) {
        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("suggestedDocumentType", "PURCHASE_INVOICE");
        draft.put("filename", filename);
        String text;
        try {
            text = new String(Base64.getDecoder().decode(contentBase64));
        } catch (Exception e) {
            text = "";
        }
        Matcher gstin = GSTIN.matcher(text);
        if (gstin.find()) {
            draft.put("gstin", gstin.group());
        }
        Matcher amount = AMOUNT.matcher(text);
        if (amount.find()) {
            draft.put("totalAmount", amount.group(1).replace(",", ""));
        }
        draft.put("currency", "INR");
        draft.put("confidence", gstin.find() || draft.containsKey("totalAmount") ? 0.62 : 0.45);
        draft.put("lineItems", List.of());
        draft.put("extractionMethod", "heuristic");
        return draft;
    }

    private static String extractJsonObject(String message) {
        int start = message.indexOf('{');
        int end = message.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return message.substring(start, end + 1);
        }
        return "{}";
    }

    private static String trimSlash(String url) {
        if (url == null || url.isBlank()) {
            return "https://api.openai.com/v1";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static Double asDouble(Object value) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        if (value instanceof String s) {
            try {
                return Double.parseDouble(s);
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private static final class TenantSafe {
        static String fingerprint(Map<String, Object> fields) {
            return String.valueOf(fields);
        }
    }
}
