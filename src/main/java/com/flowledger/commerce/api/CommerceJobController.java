package com.flowledger.commerce.api;

import com.flowledger.commerce.dto.CommerceDtos;
import com.flowledger.commerce.service.CommerceJobService;
import com.flowledger.common.dto.ApiResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/commerce/jobs")
public class CommerceJobController {
    private final CommerceJobService jobService;

    public CommerceJobController(CommerceJobService jobService) {
        this.jobService = jobService;
    }

    @GetMapping("/{jobId}")
    @PreAuthorize("hasAuthority('COMMERCE_VIEW')")
    public ApiResponse<CommerceDtos.CommerceJobResponse> getJob(@PathVariable UUID jobId) {
        return ApiResponse.of(jobService.getJob(jobId));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('COMMERCE_VIEW')")
    public ApiResponse<List<CommerceDtos.CommerceJobResponse>> listRecentJobs() {
        return ApiResponse.of(jobService.listRecentJobs());
    }
}
