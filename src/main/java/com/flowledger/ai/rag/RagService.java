package com.flowledger.ai.rag;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowledger.ai.config.AiProperties;
import com.flowledger.ai.config.ConditionalOnAiEnabled;
import com.flowledger.ai.embedding.EmbeddingPipeline;
import com.flowledger.ai.entity.AiEmbedding;
import com.flowledger.ai.entity.AiKnowledgeDocument;
import com.flowledger.ai.provider.AIProviderRegistry;
import com.flowledger.ai.repository.AiKnowledgeDocumentRepository;
import com.flowledger.common.tenant.TenantContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RAG over knowledge documents only (never invoices/stock). Prefers embedding cosine similarity when
 * vectors exist; falls back to ILIKE/title-content search.
 */
@Service
@ConditionalOnAiEnabled
public class RagService {
    private final AiProperties properties;
    private final AiKnowledgeDocumentRepository documents;
    private final EmbeddingPipeline embeddingPipeline;
    private final AIProviderRegistry providers;
    private final ObjectMapper objectMapper;

    public RagService(
            AiProperties properties,
            AiKnowledgeDocumentRepository documents,
            EmbeddingPipeline embeddingPipeline,
            AIProviderRegistry providers,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.documents = documents;
        this.embeddingPipeline = embeddingPipeline;
        this.providers = providers;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public String retrieveContext(String query, int limit) {
        if (!properties.isRagEnabled()) {
            return "";
        }
        UUID org = TenantContext.getOrganizationId();
        List<AiKnowledgeDocument> hits = retrieve(org, query, limit);
        if (hits.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("Knowledge context:\n");
        for (AiKnowledgeDocument d : hits) {
            sb.append("- ")
                    .append(d.getTitle())
                    .append(" (")
                    .append(d.getDocType())
                    .append("): ")
                    .append(truncate(d.getContent(), 600))
                    .append('\n');
        }
        return sb.toString();
    }

    @Transactional(readOnly = true)
    public List<AiKnowledgeDocument> retrieve(UUID org, String query, int limit) {
        if (query == null || query.isBlank()) {
            return documents.findByOrganizationIdOrderByUpdatedAtDesc(org).stream()
                    .limit(limit)
                    .toList();
        }
        List<AiEmbedding> embeddings = embeddingPipeline.listKnowledgeEmbeddings(org);
        if (!embeddings.isEmpty() && properties.isEmbeddingsEnabled()) {
            try {
                List<Float> q = providers.active().embed(query);
                List<Scored> scored = new ArrayList<>();
                for (AiEmbedding emb : embeddings) {
                    List<Float> v = objectMapper.readValue(emb.getEmbeddingJson(), new TypeReference<>() {});
                    scored.add(new Scored(emb.getSourceId(), cosine(q, v)));
                }
                scored.sort(Comparator.comparingDouble(Scored::score).reversed());
                List<AiKnowledgeDocument> out = new ArrayList<>();
                for (Scored s : scored) {
                    if (out.size() >= limit) {
                        break;
                    }
                    documents.findByIdAndOrganizationId(s.id(), org).ifPresent(out::add);
                }
                if (!out.isEmpty()) {
                    return out;
                }
            } catch (Exception ignored) {
                // fall through to text search
            }
        }
        String q = query.trim();
        List<AiKnowledgeDocument> textHits = documents.search(org, q);
        if (textHits.isEmpty()) {
            // token fallback
            for (String token : q.toLowerCase(Locale.ROOT).split("\\s+")) {
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

    private record Scored(UUID id, double score) {}
}
