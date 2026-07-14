package com.flowledger.search.controller;

import com.flowledger.common.dto.ApiResponse;
import com.flowledger.search.dto.SearchDtos.ReindexResponse;
import com.flowledger.search.dto.SearchDtos.Response;
import com.flowledger.search.service.GlobalSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {
    private final GlobalSearchService searchService;

    @GetMapping
    ApiResponse<Response> search(
            @RequestParam("q") String q,
            @RequestParam(value = "types", required = false) String types,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "page", required = false) Integer page) {
        return ApiResponse.of(searchService.search(q, types, limit, page));
    }

    @PostMapping("/reindex")
    @PreAuthorize("hasRole('ORGANIZATION_ADMIN')")
    ApiResponse<ReindexResponse> reindex() {
        return ApiResponse.of(searchService.reindex());
    }
}
