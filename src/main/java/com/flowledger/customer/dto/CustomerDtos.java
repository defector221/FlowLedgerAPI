package com.flowledger.customer.dto;

import jakarta.validation.constraints.*;
import java.math.*;
import java.time.LocalDate;
import java.util.*;

public final class CustomerDtos {
    private CustomerDtos() {}

    public record Create(
            String customerCode,
            @NotBlank String customerName,
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
            @DecimalMin("0.0") BigDecimal creditLimit,
            String paymentTerms,
            BigDecimal openingBalance,
            String notes) {}

    public record Update(
            @NotBlank String customerName,
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
            @DecimalMin("0.0") BigDecimal creditLimit,
            String paymentTerms,
            BigDecimal openingBalance,
            String notes,
            Boolean archived) {}

    public record Response(
            UUID id,
            String customerCode,
            String customerName,
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
            BigDecimal creditLimit,
            String paymentTerms,
            BigDecimal openingBalance,
            String notes,
            boolean archived) {}

    public record Search(String search, Boolean archived) {}

    public record StatementEntry(
            LocalDate date,
            String documentType,
            String documentNumber,
            UUID documentId,
            BigDecimal debit,
            BigDecimal credit,
            BigDecimal runningBalance) {}

    public record Statement(
            BigDecimal openingBalance,
            BigDecimal invoicesOutstanding,
            BigDecimal balance,
            BigDecimal invoicesTotal,
            BigDecimal receiptsTotal,
            List<StatementEntry> entries) {}

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
            UUID customerId, String customerName, LocalDate asOf, AgingBuckets buckets, List<AgingLine> lines) {}
}
