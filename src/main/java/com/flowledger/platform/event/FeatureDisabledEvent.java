package com.flowledger.platform.event;

import java.util.UUID;

public record FeatureDisabledEvent(UUID organizationId, String moduleCode, String featureCode) {}
