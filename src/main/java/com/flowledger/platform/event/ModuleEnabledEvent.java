package com.flowledger.platform.event;

import java.util.UUID;

public record ModuleEnabledEvent(UUID organizationId, String moduleCode) {}
