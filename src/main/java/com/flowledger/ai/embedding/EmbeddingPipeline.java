package com.flowledger.ai.embedding;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowledger.ai.config.AiProperties;
import com.flowledger.ai.config.ConditionalOnAiEnabled;
import com.flowledger.ai.entity.AiEmbedding;
import com.flowledger.ai.entity.AiKnowledgeDocument;
import com.flowledger.ai.provider.AIProviderRegistry;
import com.flowledger.ai.repository.AiEmbeddingRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnAiEnabled
public class EmbeddingPipeline {
    public static final String SOURCE_KNOWLEDGE = "KNOWLEDGE";

    private final AiProperties properties;
    private final AIProviderRegistry providers;
    private final AiEmbeddingRepository embeddings;
    private final ObjectMapper objectMapper;

    public EmbeddingPipeline(
            AiProperties properties,
            AIProviderRegistry providers,
            AiEmbeddingRepository embeddings,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.providers = providers;
        this.embeddings = embeddings;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AiEmbedding embedKnowledge(AiKnowledgeDocument doc) {
        if (!properties.isEmbeddingsEnabled()) {
            return null;
        }
        String hash = sha256(doc.getContent());
        var existing = embeddings.findByOrganizationIdAndSourceTypeAndSourceId(
                doc.getOrganizationId(), SOURCE_KNOWLEDGE, doc.getId());
        if (existing.isPresent() && hash.equals(existing.get().getContentHash())) {
            return existing.get();
        }
        List<Float> vector = providers.active().embed(doc.getTitle() + "\n" + doc.getContent());
        AiEmbedding emb = existing.orElseGet(AiEmbedding::new);
        emb.setOrganizationId(doc.getOrganizationId());
        emb.setSourceType(SOURCE_KNOWLEDGE);
        emb.setSourceId(doc.getId());
        emb.setContentHash(hash);
        try {
            emb.setEmbeddingJson(objectMapper.writeValueAsString(vector));
        } catch (JsonProcessingException e) {
            emb.setEmbeddingJson("[]");
        }
        return embeddings.save(emb);
    }

    @Transactional(readOnly = true)
    public List<AiEmbedding> listKnowledgeEmbeddings(UUID organizationId) {
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
