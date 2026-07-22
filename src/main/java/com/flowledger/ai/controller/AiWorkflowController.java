package com.flowledger.ai.controller;

import com.flowledger.ai.config.ConditionalOnAiEnabled;
import com.flowledger.ai.dto.AiDtos;
import com.flowledger.ai.workflow.AiWorkflowGateService;
import com.flowledger.ai.workflow.DocumentAiService;
import com.flowledger.ai.workflow.VoiceAiService;
import com.flowledger.ai.workflow.WorkflowDraftService;
import com.flowledger.common.security.UserPrincipal;
import com.flowledger.common.tenant.TenantContext;
import com.flowledger.transport.entity.ApprovalRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/ai/workflow")
@ConditionalOnAiEnabled
public class AiWorkflowController {
    private final WorkflowDraftService drafts;
    private final DocumentAiService documentAi;
    private final VoiceAiService voiceAi;
    private final AiWorkflowGateService gate;

    public AiWorkflowController(
            WorkflowDraftService drafts,
            DocumentAiService documentAi,
            VoiceAiService voiceAi,
            AiWorkflowGateService gate) {
        this.drafts = drafts;
        this.documentAi = documentAi;
        this.voiceAi = voiceAi;
        this.gate = gate;
    }

    @PostMapping("/suggest-from-text")
    @PreAuthorize("hasAuthority('AI_CHAT') or hasAuthority('AI_WORKFLOW') or hasRole('ORGANIZATION_ADMIN')")
    public AiDtos.WorkflowSuggestResponse suggestFromText(
            @AuthenticationPrincipal UserPrincipal principal, @RequestBody AiDtos.WorkflowSuggestRequest request) {
        ensureTenant(principal);
        return drafts.suggestFields(request);
    }

    @PostMapping("/suggest")
    @PreAuthorize("hasAuthority('AI_CHAT') or hasAuthority('AI_WORKFLOW') or hasRole('ORGANIZATION_ADMIN')")
    public AiDtos.WorkflowDraftResponse suggestNl(
            @AuthenticationPrincipal UserPrincipal principal, @RequestBody AiDtos.WorkflowNlSuggestRequest request) {
        ensureTenant(principal);
        return drafts.suggestAndCreate(request);
    }

    @GetMapping("/drafts")
    @PreAuthorize("hasAuthority('AI_CHAT') or hasAuthority('AI_WORKFLOW') or hasRole('ORGANIZATION_ADMIN')")
    public List<AiDtos.WorkflowDraftResponse> listDrafts(@AuthenticationPrincipal UserPrincipal principal) {
        ensureTenant(principal);
        return drafts.list();
    }

    @PostMapping("/drafts")
    @PreAuthorize("hasAuthority('AI_WORKFLOW') or hasAuthority('AI_ADMIN') or hasRole('ORGANIZATION_ADMIN')")
    public AiDtos.WorkflowDraftResponse createDraft(
            @AuthenticationPrincipal UserPrincipal principal, @RequestBody AiDtos.WorkflowDraftRequest request) {
        ensureTenant(principal);
        return drafts.create(request);
    }

    @PutMapping("/drafts/{id}")
    @PreAuthorize("hasAuthority('AI_WORKFLOW') or hasAuthority('AI_ADMIN') or hasRole('ORGANIZATION_ADMIN')")
    public AiDtos.WorkflowDraftResponse updateDraft(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @RequestBody AiDtos.WorkflowDraftRequest request) {
        ensureTenant(principal);
        return drafts.update(id, request);
    }

    @PostMapping("/drafts/{id}/activate")
    @PreAuthorize("hasAuthority('AI_WORKFLOW') or hasAuthority('AI_ADMIN') or hasRole('ORGANIZATION_ADMIN')")
    public AiDtos.WorkflowDraftResponse activate(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        ensureTenant(principal);
        return drafts.activate(id);
    }

    @PostMapping("/drafts/{id}/deactivate")
    @PreAuthorize("hasAuthority('AI_WORKFLOW') or hasAuthority('AI_ADMIN') or hasRole('ORGANIZATION_ADMIN')")
    public AiDtos.WorkflowDraftResponse deactivate(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        ensureTenant(principal);
        return drafts.deactivate(id);
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

    @GetMapping("/approvals")
    @PreAuthorize("hasAuthority('AI_CHAT') or hasAuthority('AI_WORKFLOW') or hasRole('ORGANIZATION_ADMIN')")
    public List<AiDtos.WorkflowApprovalResponse> listApprovals(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "pending") String status) {
        ensureTenant(principal);
        List<ApprovalRequest> rows =
                "all".equalsIgnoreCase(status) ? gate.listRecent() : gate.listPending();
        return rows.stream().map(this::toApproval).toList();
    }

    @PostMapping("/approvals/{id}/approve")
    @PreAuthorize("hasAuthority('AI_WORKFLOW') or hasAuthority('AI_ADMIN') or hasRole('ORGANIZATION_ADMIN')")
    public AiDtos.WorkflowApprovalResponse approve(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @RequestBody(required = false) AiDtos.WorkflowApprovalDecideRequest body) {
        ensureTenant(principal);
        return toApproval(gate.approve(id, body == null ? null : body.remarks()));
    }

    @PostMapping("/approvals/{id}/reject")
    @PreAuthorize("hasAuthority('AI_WORKFLOW') or hasAuthority('AI_ADMIN') or hasRole('ORGANIZATION_ADMIN')")
    public AiDtos.WorkflowApprovalResponse reject(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @RequestBody(required = false) AiDtos.WorkflowApprovalDecideRequest body) {
        ensureTenant(principal);
        return toApproval(gate.reject(id, body == null ? null : body.remarks()));
    }

    private AiDtos.WorkflowApprovalResponse toApproval(ApprovalRequest r) {
        return new AiDtos.WorkflowApprovalResponse(
                r.getId(),
                r.getEntityType(),
                r.getEntityId(),
                r.getStatus() == null ? null : r.getStatus().name(),
                r.getRequestedBy(),
                r.getRequestedAt(),
                r.getDecidedBy(),
                r.getDecidedAt(),
                r.getRemarks());
    }

    private void ensureTenant(UserPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        TenantContext.set(principal.getOrgId(), principal.getId());
    }
}
