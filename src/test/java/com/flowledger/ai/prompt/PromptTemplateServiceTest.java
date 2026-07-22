package com.flowledger.ai.prompt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PromptTemplateServiceTest {
    private PromptTemplateService service;

    @BeforeEach
    void setUp() {
        service = new PromptTemplateService();
        service.clearCache();
    }

    @Test
    void loadsAndRendersClasspathTemplate() {
        String rendered = service.render(
                "agent-ceo",
                Map.of(
                        "organizationName", "Acme",
                        "context", "KPI ok",
                        "question", "How are sales?"));
        assertTrue(rendered.contains("Acme"));
        assertTrue(rendered.contains("KPI ok"));
        assertTrue(rendered.contains("How are sales?"));
        assertTrue(rendered.contains("CEO"));
    }

    @Test
    void cachesByName() {
        String contentA = service.load("inventory-analysis");
        String contentB = service.load("inventory-analysis.md");
        assertEquals(contentA, contentB);
    }

    @Test
    void missingTemplateThrows() {
        assertThrows(IllegalArgumentException.class, () -> service.load("does-not-exist"));
    }
}
