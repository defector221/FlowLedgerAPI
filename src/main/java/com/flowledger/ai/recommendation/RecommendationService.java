package com.flowledger.ai.recommendation;

import com.flowledger.ai.config.ConditionalOnAiEnabled;
import com.flowledger.ai.dto.AiDtos;
import com.flowledger.ai.entity.AiRecommendation;
import com.flowledger.ai.repository.AiRecommendationRepository;
import com.flowledger.common.tenant.TenantContext;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@ConditionalOnAiEnabled
public class RecommendationService {
    private final AiRecommendationRepository repository;

    public RecommendationService(AiRecommendationRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public AiDtos.RecommendationResponse create(AiDtos.RecommendationCreateRequest request) {
        AiRecommendation recommendation = new AiRecommendation();
        recommendation.setOrganizationId(TenantContext.getOrganizationId());
        recommendation.setType(request.type());
        recommendation.setPriority(request.priority() == null ? "MEDIUM" : request.priority());
        recommendation.setTitle(request.title());
        recommendation.setDescription(request.description());
        recommendation.setConfidence(request.confidence());
        recommendation.setReason(request.reason());
        recommendation.setEvidence(request.evidence());
        recommendation.setSuggestedAction(request.suggestedAction());
        recommendation.setRelatedEntityType(request.relatedEntityType());
        recommendation.setRelatedEntityId(request.relatedEntityId());
        recommendation.setStatus("NEW");
        return toDto(repository.save(recommendation));
    }

    @Transactional(readOnly = true)
    public List<AiDtos.RecommendationResponse> list(String status) {
        UUID org = TenantContext.getOrganizationId();
        List<AiRecommendation> rows = status == null || status.isBlank()
                ? repository.findByOrganizationIdOrderByCreatedAtDesc(org)
                : repository.findByOrganizationIdAndStatusOrderByCreatedAtDesc(
                        org, normalizeStatus(status.trim().toUpperCase(Locale.ROOT)));
        return rows.stream().map(this::toDto).toList();
    }

    @Transactional
    public AiDtos.RecommendationResponse patch(UUID id, AiDtos.RecommendationPatchRequest request) {
        AiRecommendation recommendation = repository
                .findByIdAndOrganizationId(id, TenantContext.getOrganizationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recommendation not found"));
        if (request.status() != null && !request.status().isBlank()) {
            String status = normalizeStatus(request.status().trim().toUpperCase(Locale.ROOT));
            if (!List.of("NEW", "OPEN", "ACKNOWLEDGED", "DISMISSED", "DONE").contains(status)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status");
            }
            recommendation.setStatus(status);
        }
        return toDto(repository.save(recommendation));
    }

    /** Accept NEW (canonical) and OPEN (legacy synonym). */
    private static String normalizeStatus(String status) {
        if ("OPEN".equals(status)) {
            return "NEW";
        }
        return status;
    }

    @Transactional
    public AiDtos.RecommendationResponse acknowledge(UUID id) {
        return patch(id, new AiDtos.RecommendationPatchRequest("ACKNOWLEDGED"));
    }

    @Transactional
    public AiDtos.RecommendationResponse dismiss(UUID id) {
        return patch(id, new AiDtos.RecommendationPatchRequest("DISMISSED"));
    }

    private AiDtos.RecommendationResponse toDto(AiRecommendation recommendation) {
        return new AiDtos.RecommendationResponse(
                recommendation.getId(),
                recommendation.getType(),
                recommendation.getPriority(),
                recommendation.getTitle(),
                recommendation.getDescription(),
                recommendation.getConfidence(),
                recommendation.getReason(),
                recommendation.getEvidence(),
                recommendation.getSuggestedAction(),
                recommendation.getStatus(),
                recommendation.getRelatedEntityType(),
                recommendation.getRelatedEntityId(),
                recommendation.getCreatedAt(),
                recommendation.getUpdatedAt());
    }
}
