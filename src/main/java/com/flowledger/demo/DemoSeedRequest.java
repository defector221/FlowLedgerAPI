package com.flowledger.demo;

import java.util.Map;

public record DemoSeedRequest(
        String scenario, String mode, String organizationName, Map<String, Integer> overrides) {}
