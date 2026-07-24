package com.flowledger.finance.voucher.dto;

import com.flowledger.finance.voucher.domain.VoucherStatus;
import com.flowledger.finance.voucher.domain.VoucherType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class VoucherDtos {
    private VoucherDtos() {}

    public record VoucherLineRequest(
            @NotNull UUID accountId,
            BigDecimal debit,
            BigDecimal credit,
            String description,
            UUID costCenterId,
            UUID departmentId,
            UUID projectId,
            UUID warehouseId,
            String inventoryReference,
            UUID taxRateId,
            BigDecimal taxAmount,
            Integer sortOrder) {}

    public record VoucherRequest(
            UUID branchId,
            @NotNull VoucherType voucherType,
            @NotNull LocalDate voucherDate,
            String currencyCode,
            BigDecimal exchangeRate,
            String referenceType,
            UUID referenceId,
            String narration,
            Boolean recurring,
            UUID recurringTemplateId,
            String recurrenceRule,
            @NotEmpty @Valid List<VoucherLineRequest> lines) {}

    public record VoucherLineResponse(
            UUID id,
            UUID accountId,
            BigDecimal debit,
            BigDecimal credit,
            String description,
            UUID costCenterId,
            UUID departmentId,
            UUID projectId,
            UUID warehouseId,
            String inventoryReference,
            UUID taxRateId,
            BigDecimal taxAmount,
            int sortOrder) {}

    public record VoucherResponse(
            UUID id,
            UUID branchId,
            String voucherNumber,
            VoucherType voucherType,
            LocalDate voucherDate,
            String currencyCode,
            BigDecimal exchangeRate,
            String referenceType,
            UUID referenceId,
            String narration,
            VoucherStatus status,
            BigDecimal totalDebit,
            BigDecimal totalCredit,
            boolean posted,
            OffsetDateTime postedAt,
            UUID postedBy,
            UUID journalEntryId,
            UUID reversedVoucherId,
            UUID reversalOfId,
            boolean recurring,
            UUID recurringTemplateId,
            String recurrenceRule,
            Long version,
            List<VoucherLineResponse> lines) {}

    public record PostingResult(UUID voucherId, UUID journalEntryId, VoucherStatus status, String message) {}
}
