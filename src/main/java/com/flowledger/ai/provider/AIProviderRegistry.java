package com.flowledger.ai.provider;

import com.flowledger.ai.config.AiProperties;
import com.flowledger.ai.config.ConditionalOnAiEnabled;
import com.flowledger.common.exception.BusinessException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@ConditionalOnAiEnabled
public class AIProviderRegistry {
    private final AiProperties properties;
    private final Map<String, AIProvider> providers;

    public AIProviderRegistry(AiProperties properties, List<AIProvider> providers) {
        this.properties = properties;
        this.providers = providers.stream()
                .collect(Collectors.toMap(p -> p.name().toLowerCase(Locale.ROOT), Function.identity(), (a, b) -> a));
    }

    public AIProvider active() {
        String key = properties.getProvider() == null
                ? "openai"
                : properties.getProvider().trim().toLowerCase(Locale.ROOT);
        AIProvider provider = providers.get(key);
        if (provider == null) {
            throw new BusinessException("AI provider not configured: " + key);
        }
        return provider;
    }

    public AIProvider require(String name) {
        AIProvider provider = providers.get(name == null ? "" : name.toLowerCase(Locale.ROOT));
        if (provider == null) {
            throw new BusinessException("AI provider not found: " + name);
        }
        return provider;
    }
}
