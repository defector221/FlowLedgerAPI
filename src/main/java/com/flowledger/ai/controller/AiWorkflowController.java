package com.flowledger.ai.controller;

import com.flowledger.ai.config.ConditionalOnAiEnabled;
import com.flowledger.ai.dto.AiDtos;
import com.flowledger.ai.workflow.DocumentAiService;
import com.flowledger.ai.workflow.VoiceAiStub;
import com.flowledger.ai.workflow.WorkflowSuggestionService;
import com.flowledger.common.security.UserPrincipal;
import com.flowledger.common.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/ai/workflow")
@ConditionalOnAiEnabled
public class AiWorkflowController {
    private final WorkflowSuggestionService suggestions;
    private final DocumentAiService documentAi;
    private final VoiceAiStub voiceAi;

    public AiWorkflowController(
            WorkflowSuggestionService suggestions, DocumentAiService documentAi, VoiceAiStub voiceAi) {
        this.suggestions = suggestions;
        this.documentAi = documentAi;
        this.voiceAi = voiceAi;
    }

    @PostMapping("/suggest-from-text")
    @PreAuthorize("hasAuthority('AI_CHAT') or hasRole('ORGANIZATION_ADMIN')")
    public AiDtos.WorkflowSuggestResponse suggestFromText(
            @AuthenticationPrincipal UserPrincipal principal, @RequestBody AiDtos.WorkflowSuggestRequest request) {
        ensureTenant(principal);
        return suggestions.suggestFromText(request);
    }

    @PostMapping("/document-extract")
    @PreAuthorize("hasAuthority('AI_CHAT') or hasRole('ORGANIZATION_ADMIN')")
    public AiDtos.DocumentAiResponse documentExtract(
            @AuthenticationPrincipal UserPrincipal principal, @RequestBody AiDtos.DocumentAiRequest request) {
        ensureTenant(principal);
        return documentAi.extract(request);
    }

    @PostMapping("/voice-transcribe")
    @PreAuthorize("hasAuthority('AI_CHAT') or hasRole('ORGANIZATION_ADMIN')")
    public AiDtos.VoiceAiResponse voiceTranscribe(
            @AuthenticationPrincipal UserPrincipal principal, @RequestBody AiDtos.VoiceAiRequest request) {
        ensureTenant(principal);
        return voiceAi.transcribe(request);
    }

    private void ensureTenant(UserPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        TenantContext.set(principal.getOrgId(), principal.getId());
    }
}
