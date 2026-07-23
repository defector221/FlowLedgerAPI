package com.flowledger.ai.rag;

import com.flowledger.ai.config.ConditionalOnAiEnabled;
import com.flowledger.ai.dto.AiDtos;
import com.flowledger.ai.embedding.EmbeddingPipeline;
import com.flowledger.ai.entity.AiKnowledgeDocument;
import com.flowledger.ai.repository.AiKnowledgeDocumentRepository;
import com.flowledger.common.tenant.TenantContext;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@ConditionalOnAiEnabled
public class KnowledgeService {
    private final AiKnowledgeDocumentRepository documents;
    private final EmbeddingPipeline embeddingPipeline;
    private final RagService ragService;

    public KnowledgeService(
            AiKnowledgeDocumentRepository documents, EmbeddingPipeline embeddingPipeline, RagService ragService) {
        this.documents = documents;
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
        document.setDocType(
                request.docType() == null || request.docType().isBlank()
                        ? "GENERAL"
                        : request.docType().trim());
        document.setContent(request.content());
        document.setContentHash(EmbeddingPipeline.sha256(request.content()));
        AiKnowledgeDocument saved = documents.save(document);
        embeddingPipeline.embedKnowledge(saved);
        return toDto(saved);
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
        return new AiDtos.KnowledgeResponse(
                document.getId(),
                document.getTitle(),
                document.getDocType(),
                document.getContent(),
                document.getCreatedAt(),
                document.getUpdatedAt());
    }
}
