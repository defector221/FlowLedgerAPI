package com.flowledger.auth.dto;
import java.util.*; public record UserResponse(UUID id, UUID organizationId, String email, String firstName, String lastName, Set<String> roles) {}
