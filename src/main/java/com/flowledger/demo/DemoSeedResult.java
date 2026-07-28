package com.flowledger.demo;

import java.util.Map;
import java.util.UUID;

public record DemoSeedResult(
        String scenario,
        String status,
        UUID organizationId,
        String organizationName,
        String adminEmail,
        String message,
        int estimatedMinutes,
        long durationMs,
        Map<String, Object> counts,
        Map<String, Boolean> verification) {}
