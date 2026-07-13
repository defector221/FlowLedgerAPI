package com.flowledger.organization.dto;

import java.time.Instant;
import java.util.UUID;

public record OrganizationResponse(
        UUID id,
        String name,
        String legalName,
        String logoObjectKey,
        String email,
        String phone,
        String website,
        String gstin,
        String pan,
        String cin,
        String billingAddress,
        String shippingAddress,
        String city,
        String state,
        String stateCode,
        String country,
        String currency,
        String financialYearStart,
        String invoicePrefix,
        String invoiceNumberFormat,
        String bankName,
        String bankAccountNumber,
        String bankIfsc,
        String bankBranch,
        String upiId,
        String paymentTerms,
        boolean onboardingCompleted,
        Instant onboardingCompletedAt
) {}
