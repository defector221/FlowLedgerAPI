package com.flowledger.ai.prompt;

import com.flowledger.ai.config.ConditionalOnAiEnabled;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

@Service
@ConditionalOnAiEnabled
public class PromptTemplateService {
    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();
    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    public String render(String templateName, Map<String, String> vars) {
        String template = load(templateName);
        if (vars == null || vars.isEmpty()) {
            return template;
        }
        String result = template;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            result = result.replace("{{" + e.getKey() + "}}", e.getValue() == null ? "" : e.getValue());
        }
        return result;
    }

    public String load(String templateName) {
        String key = normalize(templateName);
        return cache.computeIfAbsent(key, this::readClasspath);
    }

    void clearCache() {
        cache.clear();
    }

    private String normalize(String name) {
        String trimmedName = name == null ? "" : name.trim();
        if (trimmedName.endsWith(".md")) {
            trimmedName = trimmedName.substring(0, trimmedName.length() - 3);
        }
        return trimmedName;
    }

    private String readClasspath(String name) {
        try {
            Resource resource = resolver.getResource("classpath:prompts/" + name + ".md");
            if (!resource.exists()) {
                throw new IllegalArgumentException("Prompt template not found: " + name);
            }
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load prompt template: " + name, e);
        }
    }
}
