package com.flowledger.ai.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowledger.ai.config.AiProperties;
import com.flowledger.ai.dto.AiDtos;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class VoiceAiServiceTest {
    @Test
    void mockTranscriptWhenNoApiKey() {
        AiProperties props = new AiProperties();
        props.setVoiceEnabled(true);
        props.getOpenai().setApiKey("");
        VoiceAiService service = new VoiceAiService(props, new ObjectMapper(), RestClient.builder());
        AiDtos.VoiceAiResponse res = service.transcribe(new AiDtos.VoiceAiRequest("audio/webm", "AAAA"));
        assertTrue(res.configured());
        assertTrue(res.transcript() != null && res.transcript().contains("Mock transcript"));
    }

    @Test
    void disabledWhenVoiceOff() {
        AiProperties props = new AiProperties();
        props.setVoiceEnabled(false);
        VoiceAiService service = new VoiceAiService(props, new ObjectMapper(), RestClient.builder());
        AiDtos.VoiceAiResponse res = service.transcribe(new AiDtos.VoiceAiRequest("audio/webm", "AAAA"));
        assertFalse(res.configured());
        assertEquals(null, res.transcript());
    }
}
