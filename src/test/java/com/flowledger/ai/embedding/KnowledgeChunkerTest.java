package com.flowledger.ai.embedding;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeChunkerTest {
    @Test
    void chunksOverlapOnLongContent() {
        String content = "a".repeat(2000);
        List<String> chunks = KnowledgeChunker.chunk(content, 800, 120);
        assertTrue(chunks.size() >= 2);
        assertEquals(800, chunks.get(0).length());
    }

    @Test
    void shortContentSingleChunk() {
        assertEquals(1, KnowledgeChunker.chunk("hello world").size());
    }
}
