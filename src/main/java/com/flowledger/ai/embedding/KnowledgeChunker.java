package com.flowledger.ai.embedding;

import java.util.ArrayList;
import java.util.List;

/** Splits knowledge content into overlapping character windows for embedding. */
public final class KnowledgeChunker {
    public static final int DEFAULT_CHUNK_SIZE = 800;
    public static final int DEFAULT_OVERLAP = 120;

    private KnowledgeChunker() {}

    public static List<String> chunk(String content) {
        return chunk(content, DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP);
    }

    public static List<String> chunk(String content, int size, int overlap) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        String text = content.trim();
        if (text.length() <= size) {
            return List.of(text);
        }
        int step = Math.max(1, size - Math.max(0, overlap));
        List<String> chunks = new ArrayList<>();
        for (int start = 0; start < text.length(); start += step) {
            int end = Math.min(text.length(), start + size);
            String piece = text.substring(start, end).trim();
            if (!piece.isEmpty()) {
                chunks.add(piece);
            }
            if (end >= text.length()) {
                break;
            }
        }
        return chunks;
    }

    public static int estimateTokens(String content) {
        if (content == null || content.isBlank()) {
            return 0;
        }
        return Math.max(1, content.length() / 4);
    }
}
