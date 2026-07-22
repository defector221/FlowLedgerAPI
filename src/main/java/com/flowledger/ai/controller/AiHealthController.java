package com.flowledger.ai.controller;

import com.flowledger.ai.config.AiProperties;
import com.flowledger.ai.config.ConditionalOnAiEnabled;
import com.flowledger.ai.dto.AiDtos;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
@ConditionalOnAiEnabled
public class AiHealthController {
    private final AiProperties properties;

    public AiHealthController(AiProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/health")
    @PreAuthorize("hasAuthority('AI_CHAT') or hasRole('ORGANIZATION_ADMIN')")
    public AiDtos.HealthResponse health() {
        return new AiDtos.HealthResponse(
                properties.isEnabled(),
                properties.getProvider(),
                properties.isChatEnabled(),
                properties.isRagEnabled(),
                properties.isEmbeddingsEnabled(),
                properties.isAnalyticsEnabled(),
                properties.isDocumentAiEnabled(),
                properties.isVoiceEnabled(),
                properties.hasApiKey());
    }
}
