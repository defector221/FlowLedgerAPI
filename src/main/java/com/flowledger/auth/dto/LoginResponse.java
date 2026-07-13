package com.flowledger.auth.dto;

import java.util.List;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        UserResponse user,
        OrganizationAccessResponse activeOrganization,
        List<OrganizationAccessResponse> organizations) {}
