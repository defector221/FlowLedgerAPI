package com.flowledger.ai.workflow;

import com.flowledger.ai.config.AiProperties;
import com.flowledger.ai.config.ConditionalOnAiEnabled;
import com.flowledger.ai.dto.AiDtos;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Voice AI stub — transcription / intent not wired. */
@Service
@ConditionalOnAiEnabled
public class VoiceAiStub {
    private final AiProperties properties;

    public VoiceAiStub(AiProperties properties) {
        this.properties = properties;
    }

    public AiDtos.VoiceAiResponse transcribe(AiDtos.VoiceAiRequest request) {
        if (!properties.isVoiceEnabled()) {
            return new AiDtos.VoiceAiResponse(
                    false, "Voice AI not configured. Set flowledger.ai.voice-enabled=true.", Map.of());
        }
        return new AiDtos.VoiceAiResponse(
                true,
                "Voice AI stub — transcription not yet implemented.",
                Map.of(
                        "audioContentType",
                        request == null || request.contentType() == null ? "" : request.contentType(),
                        "draftOnly",
                        true));
    }
}
