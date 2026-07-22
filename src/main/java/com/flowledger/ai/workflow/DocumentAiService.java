package com.flowledger.ai.workflow;

import com.flowledger.ai.config.AiProperties;
import com.flowledger.ai.config.ConditionalOnAiEnabled;
import com.flowledger.ai.dto.AiDtos;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Stub for future document OCR / extraction. Feature-flagged; never posts documents. */
@Service
@ConditionalOnAiEnabled
public class DocumentAiService {
    private final AiProperties properties;

    public DocumentAiService(AiProperties properties) {
        this.properties = properties;
    }

    public AiDtos.DocumentAiResponse extract(AiDtos.DocumentAiRequest request) {
        if (!properties.isDocumentAiEnabled()) {
            return new AiDtos.DocumentAiResponse(
                    false, "Document AI not configured. Set flowledger.ai.document-ai-enabled=true.", Map.of());
        }
        return new AiDtos.DocumentAiResponse(
                true,
                "Document AI stub — extraction not yet implemented. No document was posted.",
                Map.of(
                        "filename",
                        request == null || request.filename() == null ? "" : request.filename(),
                        "draftOnly",
                        true));
    }
}
