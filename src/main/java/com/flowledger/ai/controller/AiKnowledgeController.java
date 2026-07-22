package com.flowledger.ai.controller;

import com.flowledger.ai.config.ConditionalOnAiEnabled;
import com.flowledger.ai.dto.AiDtos;
import com.flowledger.ai.rag.KnowledgeService;
import com.flowledger.common.security.UserPrincipal;
import com.flowledger.common.tenant.TenantContext;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/ai/knowledge")
@ConditionalOnAiEnabled
public class AiKnowledgeController {
    private final KnowledgeService knowledgeService;

    public AiKnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('AI_ADMIN') or hasRole('ORGANIZATION_ADMIN')")
    public AiDtos.KnowledgeResponse create(
            @AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody AiDtos.KnowledgeCreateRequest request) {
        ensureTenant(principal);
        return knowledgeService.create(request);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('AI_CHAT') or hasAuthority('AI_ADMIN') or hasRole('ORGANIZATION_ADMIN')")
    public AiDtos.KnowledgeSearchResponse search(
            @AuthenticationPrincipal UserPrincipal principal, @RequestParam(required = false) String q) {
        ensureTenant(principal);
        List<AiDtos.KnowledgeResponse> docs = knowledgeService.search(q);
        return new AiDtos.KnowledgeSearchResponse(docs);
    }

    private void ensureTenant(UserPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        TenantContext.set(principal.getOrgId(), principal.getId());
    }
}
