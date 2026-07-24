package com.flowledger.supplier.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class SupplierDtos {
    private SupplierDtos() {}

    public record Create(
            String supplierCode,
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
            String pan,
            String email,
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
            boolean archived) {}

    public record Search(String search, Boolean archived) {}

    public record AgingLine(
            UUID documentId,
            String documentNumber,
            LocalDate documentDate,
            LocalDate dueDate,
            BigDecimal outstanding,
            int daysOverdue,
            String bucket) {}

    public record AgingBuckets(
            BigDecimal current,
            BigDecimal days1To30,
            BigDecimal days31To60,
            BigDecimal days61To90,
            BigDecimal daysOver90,
            BigDecimal total) {}

    public record AgingReport(
            UUID supplierId, String supplierName, LocalDate asOf, AgingBuckets buckets, List<AgingLine> lines) {}
}
