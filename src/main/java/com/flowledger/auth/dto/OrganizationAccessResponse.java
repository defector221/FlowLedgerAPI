package com.flowledger.auth.dto;

import java.util.Set;
import java.util.UUID;

public record OrganizationAccessResponse(
        UUID id, String organizationName, Set<String> roles, String membershipStatus, boolean onboardingCompleted) {}
