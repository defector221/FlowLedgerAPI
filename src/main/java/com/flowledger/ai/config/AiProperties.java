package com.flowledger.ai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "flowledger.ai")
public class AiProperties {
    private boolean enabled = false;
    private String provider = "OPENAI";
    private boolean chatEnabled = true;
    private boolean ragEnabled = true;
    private boolean embeddingsEnabled = true;
    private boolean analyticsEnabled = false;
    private boolean documentAiEnabled = false;
    private boolean voiceEnabled = false;
    private boolean multiAgentEnabled = true;
    private boolean workflowBuilderEnabled = true;
    private OpenAi openai = new OpenAi();

    @Getter
    @Setter
    public static class OpenAi {
        private String apiKey = "";
        private String baseUrl = "https://api.openai.com/v1";
        private String chatModel = "gpt-4o-mini";
        private String embeddingModel = "text-embedding-3-small";
        private String whisperModel = "whisper-1";
    }

    public boolean hasApiKey() {
        return openai != null && openai.getApiKey() != null && !openai.getApiKey().isBlank();
    }
}
