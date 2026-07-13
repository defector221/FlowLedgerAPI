package com.flowledger.organization.dto;

import jakarta.validation.constraints.Pattern;

public record UpdateOrganizationRequest(
        String name,
        String legalName,
        @Pattern(regexp = "^$|^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$", message = "Invalid GSTIN format")
        String gstin,
        String pan,
        String cin,
        String email,
        String phone,
        String website,
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
        String paymentTerms
) {}
