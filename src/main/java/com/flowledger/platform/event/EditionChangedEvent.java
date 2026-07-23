package com.flowledger.platform.event;

import java.util.UUID;

public record EditionChangedEvent(UUID organizationId, String previousEdition, String newEdition) {}
