package com.flowledger.ai.embedding;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowledger.ai.config.AiProperties;
import com.flowledger.ai.config.ConditionalOnAiEnabled;
import com.flowledger.ai.entity.AiEmbedding;
import com.flowledger.ai.entity.AiKnowledgeChunk;
import com.flowledger.ai.entity.AiKnowledgeDocument;
import com.flowledger.ai.provider.AIProviderRegistry;
import com.flowledger.ai.repository.AiEmbeddingRepository;
import com.flowledger.ai.repository.AiKnowledgeChunkRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnAiEnabled
public class EmbeddingPipeline {
    public static final String SOURCE_KNOWLEDGE = "KNOWLEDGE";
    public static final String SOURCE_KNOWLEDGE_CHUNK = "KNOWLEDGE_CHUNK";

    private final AiProperties properties;
    private final AIProviderRegistry providers;
    private final AiEmbeddingRepository embeddings;
    private final AiKnowledgeChunkRepository chunks;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbc;

    public EmbeddingPipeline(
            AiProperties properties,
            AIProviderRegistry providers,
            AiEmbeddingRepository embeddings,
            AiKnowledgeChunkRepository chunks,
            ObjectMapper objectMapper,
            JdbcTemplate jdbc) {
        this.properties = properties;
        this.providers = providers;
        this.embeddings = embeddings;
        this.chunks = chunks;
        this.objectMapper = objectMapper;
        this.jdbc = jdbc;
    }

    @Transactional
    public void embedKnowledge(AiKnowledgeDocument doc) {
        if (!properties.isEmbeddingsEnabled()) {
            return;
        }
        chunks.deleteByDocumentId(doc.getId());
        embeddings
                .findByOrganizationIdAndSourceTypeAndSourceId(doc.getOrganizationId(), SOURCE_KNOWLEDGE, doc.getId())
                .ifPresent(embeddings::delete);

        List<String> pieces = KnowledgeChunker.chunk(doc.getTitle() + "\n\n" + doc.getContent());
        int index = 0;
        for (String piece : pieces) {
            AiKnowledgeChunk chunk = new AiKnowledgeChunk();
            chunk.setOrganizationId(doc.getOrganizationId());
            chunk.setDocumentId(doc.getId());
            chunk.setChunkIndex(index);
            chunk.setContent(piece);
            chunk.setContentHash(sha256(piece));
            chunk.setTokenEstimate(KnowledgeChunker.estimateTokens(piece));
            AiKnowledgeChunk saved = chunks.save(chunk);
            upsertChunkEmbedding(doc, saved, piece);
            index++;
        }
    }

    @Transactional
    public int reindexOrganization(UUID organizationId, List<AiKnowledgeDocument> documents) {
        int count = 0;
        for (AiKnowledgeDocument doc : documents) {
            if (!doc.getOrganizationId().equals(organizationId)) {
                continue;
            }
            embedKnowledge(doc);
            count++;
        }
        return count;
    }

    private void upsertChunkEmbedding(AiKnowledgeDocument doc, AiKnowledgeChunk chunk, String piece) {
        List<Float> vector = providers.active().embed(piece);
        AiEmbedding emb = embeddings
                .findByOrganizationIdAndSourceTypeAndSourceId(
                        doc.getOrganizationId(), SOURCE_KNOWLEDGE_CHUNK, chunk.getId())
                .orElseGet(AiEmbedding::new);
        emb.setOrganizationId(doc.getOrganizationId());
        emb.setSourceType(SOURCE_KNOWLEDGE_CHUNK);
        emb.setSourceId(chunk.getId());
        emb.setDocumentId(doc.getId());
        emb.setChunkIndex(chunk.getChunkIndex());
        emb.setContentHash(chunk.getContentHash());
        try {
            emb.setEmbeddingJson(objectMapper.writeValueAsString(vector));
        } catch (JsonProcessingException e) {
            emb.setEmbeddingJson("[]");
        }
        embeddings.save(emb);
        tryPersistPgVector(emb.getId(), vector);
    }

    private void tryPersistPgVector(UUID embeddingId, List<Float> vector) {
        if (vector == null || vector.isEmpty() || vector.size() != 1536) {
            return;
        }
        try {
            String literal = toPgVectorLiteral(vector);
            jdbc.update("UPDATE ai_embeddings SET embedding = CAST(? AS vector) WHERE id = ?", literal, embeddingId);
        } catch (Exception ignored) {
            // Extension or column may be unavailable in local/dev.
        }
    }

    public static String toPgVectorLiteral(List<Float> vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector.get(i));
        }
        return sb.append(']').toString();
    }

    @Transactional(readOnly = true)
    public List<AiEmbedding> listKnowledgeChunkEmbeddings(UUID organizationId) {
        return embeddings.findByOrganizationIdAndSourceType(organizationId, SOURCE_KNOWLEDGE_CHUNK);
    }

    /** @deprecated Prefer chunk embeddings. Kept for legacy rows. */
    @Transactional(readOnly = true)
    public List<AiEmbedding> listKnowledgeEmbeddings(UUID organizationId) {
        List<AiEmbedding> chunked = listKnowledgeChunkEmbeddings(organizationId);
        if (!chunked.isEmpty()) {
            return chunked;
        }
        return embeddings.findByOrganizationIdAndSourceType(organizationId, SOURCE_KNOWLEDGE);
    }

    public static String sha256(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest((content == null ? "" : content).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(dig);
        } catch (Exception e) {
            return Integer.toHexString((content == null ? "" : content).hashCode());
        }
    }
}
