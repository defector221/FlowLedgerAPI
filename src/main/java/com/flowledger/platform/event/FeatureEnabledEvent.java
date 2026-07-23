package com.flowledger.platform.event;

import java.util.UUID;

public record FeatureEnabledEvent(UUID organizationId, String moduleCode, String featureCode) {}
