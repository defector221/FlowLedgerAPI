package com.flowledger.platform.document.controller;

import com.flowledger.common.dto.ApiResponse;
import com.flowledger.platform.document.entity.DocumentComment;
import com.flowledger.platform.document.entity.DocumentTag;
import com.flowledger.platform.document.service.DocumentCollaborationService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentCollaborationController {
    private final DocumentCollaborationService service;

    public DocumentCollaborationController(DocumentCollaborationService service) {
        this.service = service;
    }

    @GetMapping("/attachments")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> listAttachments(
            @RequestParam String entityType, @RequestParam UUID entityId) {
        return ApiResponse.of(service.listAttachments(entityType, entityId));
    }

    @PostMapping("/attachments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ATTACHMENT_WRITE') or hasRole('ORGANIZATION_ADMIN')")
    public ApiResponse<Map<String, Object>> upload(
            @RequestParam String entityType, @RequestParam UUID entityId, @RequestParam("file") MultipartFile file) {
        var a = service.addAttachment(entityType, entityId, file);
        return ApiResponse.of(Map.of("id", a.getId(), "fileName", a.getFileName()));
    }

    @DeleteMapping("/attachments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('ATTACHMENT_WRITE') or hasRole('ORGANIZATION_ADMIN')")
    public void deleteAttachment(@PathVariable UUID id) {
        service.deleteAttachment(id);
    }

    @GetMapping("/comments")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<DocumentComment>> comments(@RequestParam String entityType, @RequestParam UUID entityId) {
        return ApiResponse.of(service.listComments(entityType, entityId));
    }

    @PostMapping("/comments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('COMMENT_WRITE') or hasRole('ORGANIZATION_ADMIN') or isAuthenticated()")
    public ApiResponse<DocumentComment> addComment(@RequestBody Map<String, Object> body) {
        return ApiResponse.of(service.addComment(
                String.valueOf(body.get("entityType")),
                UUID.fromString(String.valueOf(body.get("entityId"))),
                String.valueOf(body.get("body")),
                body.get("parentId") == null ? null : UUID.fromString(String.valueOf(body.get("parentId")))));
    }

    @GetMapping("/tags")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<DocumentTag>> tags(@RequestParam String entityType, @RequestParam UUID entityId) {
        return ApiResponse.of(service.listTags(entityType, entityId));
    }

    @PostMapping("/tags")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<DocumentTag> addTag(@RequestBody Map<String, String> body) {
        return ApiResponse.of(
                service.addTag(body.get("entityType"), UUID.fromString(body.get("entityId")), body.get("tag")));
    }

    @DeleteMapping("/tags")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("isAuthenticated()")
    public void removeTag(@RequestParam String entityType, @RequestParam UUID entityId, @RequestParam String tag) {
        service.removeTag(entityType, entityId, tag);
    }
}
