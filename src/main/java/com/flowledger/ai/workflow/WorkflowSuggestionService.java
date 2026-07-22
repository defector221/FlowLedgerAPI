package com.flowledger.ai.workflow;

import com.flowledger.ai.config.AiProperties;
import com.flowledger.ai.config.ConditionalOnAiEnabled;
import com.flowledger.ai.dto.AiDtos;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/** Draft document-field suggestions from free text. Never creates PI/PO or posts documents. */
@Service
@ConditionalOnAiEnabled
public class WorkflowSuggestionService {
    private static final Pattern AMOUNT =
            Pattern.compile("(?i)(?:rs\\.?|inr|₹)\\s*([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]+)?)");
    private static final Pattern GSTIN = Pattern.compile("\\b([0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z])\\b");

    private final AiProperties properties;

    public WorkflowSuggestionService(AiProperties properties) {
        this.properties = properties;
    }

    public AiDtos.WorkflowSuggestResponse suggestFromText(AiDtos.WorkflowSuggestRequest request) {
        if (!properties.isDocumentAiEnabled()) {
            return new AiDtos.WorkflowSuggestResponse(
                    false, "Document AI not configured. Set flowledger.ai.document-ai-enabled=true.", Map.of());
        }
        String text =
                request == null || request.text() == null ? "" : request.text().trim();
        if (text.isBlank()) {
            return new AiDtos.WorkflowSuggestResponse(false, "Text is required", Map.of());
        }

        Map<String, Object> fields = new LinkedHashMap<>();
        String lower = text.toLowerCase(Locale.ROOT);
        String docType = "UNKNOWN";
        if (lower.contains("purchase order") || lower.contains(" po ")) {
            docType = "PURCHASE_ORDER";
        } else if (lower.contains("purchase invoice") || lower.contains("bill")) {
            docType = "PURCHASE_INVOICE";
        } else if (lower.contains("sales invoice") || lower.contains("tax invoice")) {
            docType = "SALES_INVOICE";
        } else if (lower.contains("quotation") || lower.contains("quote")) {
            docType = "QUOTATION";
        }
        fields.put("suggestedDocumentType", docType);

        Matcher amount = AMOUNT.matcher(text);
        if (amount.find()) {
            fields.put("suggestedAmount", amount.group(1).replace(",", ""));
        }
        Matcher gstin = GSTIN.matcher(text.toUpperCase(Locale.ROOT));
        if (gstin.find()) {
            fields.put("suggestedGstin", gstin.group(1));
        }
        fields.put("draftOnly", true);
        fields.put("note", "Review suggested fields before creating any document. No PI/PO was created.");

        return new AiDtos.WorkflowSuggestResponse(true, "Draft suggestions only — document not created.", fields);
    }
}
