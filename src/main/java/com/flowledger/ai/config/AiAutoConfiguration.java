package com.flowledger.ai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowledger.ai.provider.AIProvider;
import com.flowledger.ai.provider.AIProviderRegistry;
import com.flowledger.ai.provider.AzureOpenAiProvider;
import com.flowledger.ai.provider.ClaudeProvider;
import com.flowledger.ai.provider.GeminiProvider;
import com.flowledger.ai.provider.OllamaProvider;
import com.flowledger.ai.provider.OpenAIProvider;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@ConditionalOnAiEnabled
@Slf4j
public class AiAutoConfiguration {

    public AiAutoConfiguration() {
        log.info("FlowLedger AI module enabled — registering AI providers and routes");
    }

    @Bean
    OpenAIProvider openAIProvider(
            AiProperties properties, ObjectMapper objectMapper, RestClient.Builder restClientBuilder) {
        return new OpenAIProvider(properties, objectMapper, restClientBuilder.build());
    }

    @Bean
    ClaudeProvider claudeProvider() {
        return new ClaudeProvider();
    }

    @Bean
    GeminiProvider geminiProvider() {
        return new GeminiProvider();
    }

    @Bean
    AzureOpenAiProvider azureOpenAiProvider() {
        return new AzureOpenAiProvider();
    }

    @Bean
    OllamaProvider ollamaProvider() {
        return new OllamaProvider();
    }

    @Bean
    AIProviderRegistry aiProviderRegistry(AiProperties properties, List<AIProvider> providers) {
        return new AIProviderRegistry(properties, providers);
    }
}
