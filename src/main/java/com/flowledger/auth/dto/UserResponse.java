package com.flowledger.auth.dto;

import java.util.UUID;

public record UserResponse(UUID id, String email, String firstName, String lastName, String status) {}
