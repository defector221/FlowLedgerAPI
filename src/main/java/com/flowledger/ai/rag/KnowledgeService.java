package com.flowledger.ai.rag;

import com.flowledger.ai.config.ConditionalOnAiEnabled;
import com.flowledger.ai.dto.AiDtos;
import com.flowledger.ai.embedding.EmbeddingPipeline;
import com.flowledger.ai.entity.AiKnowledgeDocument;
import com.flowledger.ai.repository.AiEmbeddingRepository;
import com.flowledger.ai.repository.AiKnowledgeChunkRepository;
import com.flowledger.ai.repository.AiKnowledgeDocumentRepository;
import com.flowledger.common.tenant.TenantContext;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@ConditionalOnAiEnabled
public class KnowledgeService {
    private final AiKnowledgeDocumentRepository documents;
    private final AiKnowledgeChunkRepository chunks;
    private final AiEmbeddingRepository embeddings;
    private final EmbeddingPipeline embeddingPipeline;
    private final RagService ragService;

    public KnowledgeService(
            AiKnowledgeDocumentRepository documents,
            AiKnowledgeChunkRepository chunks,
            AiEmbeddingRepository embeddings,
            EmbeddingPipeline embeddingPipeline,
            RagService ragService) {
        this.documents = documents;
        this.chunks = chunks;
        this.embeddings = embeddings;
        this.embeddingPipeline = embeddingPipeline;
        this.ragService = ragService;
    }

    @Transactional
    public AiDtos.KnowledgeResponse create(AiDtos.KnowledgeCreateRequest request) {
        if (request.title() == null
                || request.title().isBlank()
                || request.content() == null
                || request.content().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title and content are required");
        }
        AiKnowledgeDocument document = new AiKnowledgeDocument();
        document.setOrganizationId(TenantContext.getOrganizationId());
        document.setTitle(request.title().trim());
        document.setDocType(normalizeDocType(request.docType()));
        document.setContent(request.content());
        document.setContentHash(EmbeddingPipeline.sha256(request.content()));
        AiKnowledgeDocument saved = documents.save(document);
        embeddingPipeline.embedKnowledge(saved);
        return toDto(saved);
    }

    @Transactional
    public AiDtos.KnowledgeResponse update(UUID id, AiDtos.KnowledgeUpdateRequest request) {
        UUID org = TenantContext.getOrganizationId();
        AiKnowledgeDocument document = documents
                .findByIdAndOrganizationId(id, org)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge document not found"));
        if (request.title() != null && !request.title().isBlank()) {
            document.setTitle(request.title().trim());
        }
        if (request.docType() != null && !request.docType().isBlank()) {
            document.setDocType(normalizeDocType(request.docType()));
        }
        if (request.content() != null && !request.content().isBlank()) {
            document.setContent(request.content());
            document.setContentHash(EmbeddingPipeline.sha256(request.content()));
        }
        AiKnowledgeDocument saved = documents.save(document);
        embeddingPipeline.embedKnowledge(saved);
        return toDto(saved);
    }

    @Transactional
    public void delete(UUID id) {
        UUID org = TenantContext.getOrganizationId();
        AiKnowledgeDocument document = documents
                .findByIdAndOrganizationId(id, org)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge document not found"));
        chunks.deleteByDocumentId(document.getId());
        embeddings.deleteByOrganizationIdAndSourceTypeAndSourceId(
                org, EmbeddingPipeline.SOURCE_KNOWLEDGE, document.getId());
        documents.delete(document);
    }

    @Transactional
    public AiDtos.KnowledgeResponse upload(AiDtos.KnowledgeUploadRequest request) {
        if (request == null || request.contentBase64() == null || request.contentBase64().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "contentBase64 is required");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(request.contentBase64());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid base64 content");
        }
        String filename = request.filename() == null || request.filename().isBlank()
                ? "upload.txt"
                : request.filename().trim();
        String text = extractText(filename, bytes);
        if (text.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not extract text from upload");
        }
        String title = filename.contains(".") ? filename.substring(0, filename.lastIndexOf('.')) : filename;
        return create(new AiDtos.KnowledgeCreateRequest(title, normalizeDocType(request.docType()), text));
    }

    @Transactional
    public AiDtos.KnowledgeReindexResponse reindex() {
        UUID org = TenantContext.getOrganizationId();
        List<AiKnowledgeDocument> docs = documents.findByOrganizationIdOrderByUpdatedAtDesc(org);
        int count = embeddingPipeline.reindexOrganization(org, docs);
        return new AiDtos.KnowledgeReindexResponse(count, "Re-embedded " + count + " knowledge documents");
    }

    @Transactional(readOnly = true)
    public List<AiDtos.KnowledgeResponse> search(String query) {
        UUID org = TenantContext.getOrganizationId();
        List<AiKnowledgeDocument> docs = (query == null || query.isBlank())
                ? documents.findByOrganizationIdOrderByUpdatedAtDesc(org)
                : ragService.retrieve(org, query, 20);
        return docs.stream().map(this::toDto).toList();
    }

    private AiDtos.KnowledgeResponse toDto(AiKnowledgeDocument document) {
        int chunkCount = chunks.findByDocumentIdOrderByChunkIndexAsc(document.getId()).size();
        return new AiDtos.KnowledgeResponse(
                document.getId(),
                document.getTitle(),
                document.getDocType(),
                document.getContent(),
                chunkCount,
                document.getCreatedAt(),
                document.getUpdatedAt());
    }

    private static String normalizeDocType(String docType) {
        return docType == null || docType.isBlank() ? "GENERAL" : docType.trim();
    }

    private static String extractText(String filename, byte[] bytes) {
        String lower = filename.toLowerCase(Locale.ROOT);
        String raw = new String(bytes, StandardCharsets.UTF_8);
        if (lower.endsWith(".md") || lower.endsWith(".txt") || lower.endsWith(".csv") || lower.endsWith(".json")) {
            return raw;
        }
        if (lower.endsWith(".pdf")) {
            // Lightweight extract: keep printable ASCII/UTF-8 runs from PDF streams.
            StringBuilder sb = new StringBuilder();
            for (String line : raw.split("\\R")) {
                String cleaned = line.replaceAll("[^\\p{Print}\\s]", " ").trim();
                if (cleaned.length() >= 20) {
                    sb.append(cleaned).append('\n');
                }
            }
            return sb.toString().trim();
        }
        return raw;
    }
}
