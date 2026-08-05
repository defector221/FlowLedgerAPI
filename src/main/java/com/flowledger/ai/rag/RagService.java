package com.flowledger.ai.rag;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowledger.ai.config.AiProperties;
import com.flowledger.ai.config.ConditionalOnAiEnabled;
import com.flowledger.ai.dto.AiDtos;
import com.flowledger.ai.embedding.EmbeddingPipeline;
import com.flowledger.ai.entity.AiEmbedding;
import com.flowledger.ai.entity.AiKnowledgeChunk;
import com.flowledger.ai.entity.AiKnowledgeDocument;
import com.flowledger.ai.provider.AIProviderRegistry;
import com.flowledger.ai.repository.AiKnowledgeChunkRepository;
import com.flowledger.ai.repository.AiKnowledgeDocumentRepository;
import com.flowledger.common.tenant.TenantContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RAG over knowledge chunks (never invoices/stock). Prefers pgvector ANN, then in-memory cosine,
 * then ILIKE fallback.
 */
@Service
@ConditionalOnAiEnabled
public class RagService {
    private final AiProperties properties;
    private final AiKnowledgeDocumentRepository documents;
    private final AiKnowledgeChunkRepository chunks;
    private final EmbeddingPipeline embeddingPipeline;
    private final AIProviderRegistry providers;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbc;

    public RagService(
            AiProperties properties,
            AiKnowledgeDocumentRepository documents,
            AiKnowledgeChunkRepository chunks,
            EmbeddingPipeline embeddingPipeline,
            AIProviderRegistry providers,
            ObjectMapper objectMapper,
            JdbcTemplate jdbc) {
        this.properties = properties;
        this.documents = documents;
        this.chunks = chunks;
        this.embeddingPipeline = embeddingPipeline;
        this.providers = providers;
        this.objectMapper = objectMapper;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public RetrievalResult retrieveWithCitations(String query, int limit) {
        if (!properties.isRagEnabled()) {
            return RetrievalResult.empty();
        }
        UUID org = TenantContext.getOrganizationId();
        List<AiDtos.Citation> citations = retrieveCitations(org, query, limit);
        if (citations.isEmpty()) {
            return RetrievalResult.empty();
        }
        StringBuilder sb = new StringBuilder("Knowledge context:\n");
        for (AiDtos.Citation c : citations) {
            sb.append("- [")
                    .append(c.title())
                    .append("] (")
                    .append(c.docType())
                    .append("): ")
                    .append(c.excerpt())
                    .append('\n');
        }
        return new RetrievalResult(sb.toString(), citations);
    }

    @Transactional(readOnly = true)
    public String retrieveContext(String query, int limit) {
        return retrieveWithCitations(query, limit).context();
    }

    @Transactional(readOnly = true)
    public List<AiKnowledgeDocument> retrieve(UUID org, String query, int limit) {
        List<AiDtos.Citation> citations = retrieveCitations(org, query, limit);
        Map<UUID, AiKnowledgeDocument> byId = new LinkedHashMap<>();
        for (AiDtos.Citation citation : citations) {
            if (citation.documentId() == null || byId.containsKey(citation.documentId())) {
                continue;
            }
            documents.findByIdAndOrganizationId(citation.documentId(), org).ifPresent(d -> byId.put(d.getId(), d));
        }
        if (!byId.isEmpty()) {
            return List.copyOf(byId.values());
        }
        if (query == null || query.isBlank()) {
            return documents.findByOrganizationIdOrderByUpdatedAtDesc(org).stream()
                    .limit(limit)
                    .toList();
        }
        return textSearchDocs(org, query, limit);
    }

    private List<AiDtos.Citation> retrieveCitations(UUID org, String query, int limit) {
        if (query == null || query.isBlank()) {
            return documents.findByOrganizationIdOrderByUpdatedAtDesc(org).stream()
                    .limit(limit)
                    .map(d -> new AiDtos.Citation(
                            d.getId(), null, d.getTitle(), d.getDocType(), truncate(d.getContent(), 400), null))
                    .toList();
        }
        if (properties.isEmbeddingsEnabled()) {
            List<AiDtos.Citation> ann = tryPgVectorAnn(org, query, limit);
            if (!ann.isEmpty()) {
                return ann;
            }
            List<AiDtos.Citation> cosine = tryCosineOnChunks(org, query, limit);
            if (!cosine.isEmpty()) {
                return cosine;
            }
        }
        return textSearchDocs(org, query, limit).stream()
                .map(d -> new AiDtos.Citation(
                        d.getId(), null, d.getTitle(), d.getDocType(), truncate(d.getContent(), 400), null))
                .toList();
    }

    private List<AiDtos.Citation> tryPgVectorAnn(UUID org, String query, int limit) {
        try {
            List<Float> queryVector = providers.active().embed(query);
            if (queryVector.size() != 1536) {
                return List.of();
            }
            String literal = EmbeddingPipeline.toPgVectorLiteral(queryVector);
            List<Map<String, Object>> rows = jdbc.queryForList(
                    """
                    SELECT e.source_id AS chunk_id, e.document_id AS document_id,
                           1 - (e.embedding <=> CAST(? AS vector)) AS score
                    FROM ai_embeddings e
                    WHERE e.organization_id = ?
                      AND e.source_type = ?
                      AND e.embedding IS NOT NULL
                    ORDER BY e.embedding <=> CAST(? AS vector)
                    LIMIT ?
                    """,
                    literal,
                    org,
                    EmbeddingPipeline.SOURCE_KNOWLEDGE_CHUNK,
                    literal,
                    limit);
            return toCitationsFromRows(org, rows);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<AiDtos.Citation> tryCosineOnChunks(UUID org, String query, int limit) {
        try {
            List<AiEmbedding> embeddings = embeddingPipeline.listKnowledgeChunkEmbeddings(org);
            if (embeddings.isEmpty()) {
                embeddings = embeddingPipeline.listKnowledgeEmbeddings(org);
            }
            if (embeddings.isEmpty()) {
                return List.of();
            }
            List<Float> queryVector = providers.active().embed(query);
            List<Scored> scored = new ArrayList<>();
            for (AiEmbedding emb : embeddings) {
                List<Float> vector = objectMapper.readValue(emb.getEmbeddingJson(), new TypeReference<>() {});
                scored.add(new Scored(emb.getSourceId(), emb.getDocumentId(), emb.getSourceType(), cosine(queryVector, vector)));
            }
            scored.sort(Comparator.comparingDouble(Scored::score).reversed());
            List<AiDtos.Citation> out = new ArrayList<>();
            for (Scored s : scored) {
                if (out.size() >= limit) {
                    break;
                }
                if (EmbeddingPipeline.SOURCE_KNOWLEDGE_CHUNK.equals(s.sourceType())) {
                    chunks.findById(s.sourceId()).ifPresent(chunk -> documents
                            .findByIdAndOrganizationId(chunk.getDocumentId(), org)
                            .ifPresent(doc -> out.add(new AiDtos.Citation(
                                    doc.getId(),
                                    chunk.getId(),
                                    doc.getTitle(),
                                    doc.getDocType(),
                                    truncate(chunk.getContent(), 400),
                                    s.score()))));
                } else {
                    documents.findByIdAndOrganizationId(s.sourceId(), org).ifPresent(doc -> out.add(new AiDtos.Citation(
                            doc.getId(),
                            null,
                            doc.getTitle(),
                            doc.getDocType(),
                            truncate(doc.getContent(), 400),
                            s.score())));
                }
            }
            return out;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<AiDtos.Citation> toCitationsFromRows(UUID org, List<Map<String, Object>> rows) {
        List<AiDtos.Citation> out = new ArrayList<>();
        Map<UUID, AiKnowledgeDocument> docCache = new HashMap<>();
        for (Map<String, Object> row : rows) {
            UUID chunkId = (UUID) row.get("chunk_id");
            UUID documentId = (UUID) row.get("document_id");
            Double score = row.get("score") == null ? null : ((Number) row.get("score")).doubleValue();
            AiKnowledgeChunk chunk = chunks.findById(chunkId).orElse(null);
            if (chunk == null) {
                continue;
            }
            UUID docId = documentId != null ? documentId : chunk.getDocumentId();
            AiKnowledgeDocument doc = docCache.computeIfAbsent(
                    docId, id -> documents.findByIdAndOrganizationId(id, org).orElse(null));
            if (doc == null) {
                continue;
            }
            out.add(new AiDtos.Citation(
                    doc.getId(), chunk.getId(), doc.getTitle(), doc.getDocType(), truncate(chunk.getContent(), 400), score));
        }
        return out;
    }

    private List<AiKnowledgeDocument> textSearchDocs(UUID org, String query, int limit) {
        String trimmedQuery = query.trim();
        List<AiKnowledgeDocument> textHits = documents.search(org, trimmedQuery);
        if (textHits.isEmpty()) {
            for (String token : trimmedQuery.toLowerCase(Locale.ROOT).split("\\s+")) {
                if (token.length() < 3) {
                    continue;
                }
                textHits = documents.search(org, token);
                if (!textHits.isEmpty()) {
                    break;
                }
            }
        }
        return textHits.stream().limit(limit).toList();
    }

    private static double cosine(List<Float> a, List<Float> b) {
        int n = Math.min(a.size(), b.size());
        if (n == 0) {
            return 0;
        }
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < n; i++) {
            double x = a.get(i);
            double y = b.get(i);
            dot += x * y;
            na += x * x;
            nb += y * y;
        }
        if (na == 0 || nb == 0) {
            return 0;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    public record RetrievalResult(String context, List<AiDtos.Citation> citations) {
        public static RetrievalResult empty() {
            return new RetrievalResult("", List.of());
        }
    }

    private record Scored(UUID sourceId, UUID documentId, String sourceType, double score) {}
}
