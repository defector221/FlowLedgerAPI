package com.flowledger.platform.history.controller;

import com.flowledger.common.dto.ApiResponse;
import com.flowledger.platform.history.entity.DocumentHistoryEntry;
import com.flowledger.platform.history.service.DocumentHistoryService;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/document-history")
public class DocumentHistoryController {
    private final DocumentHistoryService service;

    public DocumentHistoryController(DocumentHistoryService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<DocumentHistoryEntry>> timeline(
            @RequestParam String entityType, @RequestParam UUID entityId) {
        return ApiResponse.of(service.timeline(entityType, entityId));
    }
}
