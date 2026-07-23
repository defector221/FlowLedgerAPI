package com.flowledger.platform.event;

import java.util.UUID;

public record ModuleDisabledEvent(UUID organizationId, String moduleCode) {}
