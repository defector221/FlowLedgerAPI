package com.flowledger.supplier.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

public final class SupplierDtos {
    private SupplierDtos() {}

    public record Create(
            @NotBlank String supplierCode,
            @NotBlank String supplierName,
            String companyName,
            String gstin,
            String pan,
            @Email String email,
            String phone,
            String billingAddress,
            String shippingAddress,
            String city,
            String state,
            String stateCode,
            String country,
            String paymentTerms,
            BigDecimal openingBalance,
            String bankName,
            String bankAccountNumber,
            String bankIfsc,
            String notes) {}

    public record Update(
            @NotBlank String supplierName,
            String companyName,
            String gstin,
            String pan,
            @Email String email,
            String phone,
            String billingAddress,
            String shippingAddress,
            String city,
            String state,
            String stateCode,
            String country,
            String paymentTerms,
            BigDecimal openingBalance,
            String bankName,
            String bankAccountNumber,
            String bankIfsc,
            String notes,
            Boolean archived) {}

    public record Response(
            UUID id,
            String supplierCode,
            String supplierName,
            String companyName,
            String gstin,
            String email,
            String phone,
            String city,
            String state,
            String stateCode,
            BigDecimal openingBalance,
            boolean archived) {}

    public record Search(String search, Boolean archived) {}
}
