package com.flowledger.finance.voucher.adapter;

import com.flowledger.finance.voucher.domain.VoucherType;
import com.flowledger.finance.voucher.dto.VoucherDtos.VoucherLineRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Intermediate result from document → voucher builders, ready for {@code VoucherService.createFromDocument}. */
public record BuiltDocumentVoucher(
        VoucherType type,
        LocalDate voucherDate,
        UUID branchId,
        String currencyCode,
        BigDecimal exchangeRate,
        String referenceType,
        UUID referenceId,
        String narration,
        List<VoucherLineRequest> lines) {}
