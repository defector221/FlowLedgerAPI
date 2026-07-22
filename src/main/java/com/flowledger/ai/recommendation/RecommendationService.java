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
        AiRecommendation r = new AiRecommendation();
        r.setOrganizationId(TenantContext.getOrganizationId());
        r.setType(request.type());
        r.setPriority(request.priority() == null ? "MEDIUM" : request.priority());
        r.setTitle(request.title());
        r.setDescription(request.description());
        r.setConfidence(request.confidence());
        r.setReason(request.reason());
        r.setEvidence(request.evidence());
        r.setSuggestedAction(request.suggestedAction());
        r.setRelatedEntityType(request.relatedEntityType());
        r.setRelatedEntityId(request.relatedEntityId());
        r.setStatus("NEW");
        return toDto(repository.save(r));
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
        AiRecommendation r = repository
                .findByIdAndOrganizationId(id, TenantContext.getOrganizationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recommendation not found"));
        if (request.status() != null && !request.status().isBlank()) {
            String status = normalizeStatus(request.status().trim().toUpperCase(Locale.ROOT));
            if (!List.of("NEW", "OPEN", "ACKNOWLEDGED", "DISMISSED", "DONE").contains(status)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status");
            }
            r.setStatus(status);
        }
        return toDto(repository.save(r));
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

    private AiDtos.RecommendationResponse toDto(AiRecommendation r) {
        return new AiDtos.RecommendationResponse(
                r.getId(),
                r.getType(),
                r.getPriority(),
                r.getTitle(),
                r.getDescription(),
                r.getConfidence(),
                r.getReason(),
                r.getEvidence(),
                r.getSuggestedAction(),
                r.getStatus(),
                r.getRelatedEntityType(),
                r.getRelatedEntityId(),
                r.getCreatedAt(),
                r.getUpdatedAt());
    }
}
