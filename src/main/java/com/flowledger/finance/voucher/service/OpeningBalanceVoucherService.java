package com.flowledger.finance.voucher.service;

import com.flowledger.common.exception.BusinessException;
import com.flowledger.common.tenant.TenantContext;
import com.flowledger.finance.voucher.domain.VoucherType;
import com.flowledger.finance.voucher.dto.VoucherDtos.PostingResult;
import com.flowledger.finance.voucher.dto.VoucherDtos.VoucherLineRequest;
import com.flowledger.finance.voucher.dto.VoucherDtos.VoucherResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates and posts an opening-balance journal as an {@link VoucherType#OPENING_BALANCE} voucher.
 */
@Service
public class OpeningBalanceVoucherService {
    public static final String REFERENCE_TYPE = "OPENING_BALANCE";

    private final VoucherService vouchers;
    private final PostingEngine posting;

    public OpeningBalanceVoucherService(VoucherService vouchers, PostingEngine posting) {
        this.vouchers = vouchers;
        this.posting = posting;
    }

    @Transactional
    public VoucherResponse createOpeningJournal(UUID org, LocalDate date, List<VoucherLineRequest> lines) {
        if (org == null) {
            throw new BusinessException("Organization is required");
        }
        if (!org.equals(TenantContext.getOrganizationId())) {
            throw new BusinessException("Organization does not match tenant context");
        }
        if (lines == null || lines.isEmpty()) {
            throw new BusinessException("Opening balance lines are required");
        }
        LocalDate voucherDate = date != null ? date : LocalDate.now();
        UUID referenceId = UUID.randomUUID();
        VoucherResponse created = vouchers.createFromDocument(
                VoucherType.OPENING_BALANCE,
                voucherDate,
                null,
                "INR",
                BigDecimal.ONE,
                REFERENCE_TYPE,
                referenceId,
                "Opening balance",
                lines);
        PostingResult result = posting.post(created.id());
        return vouchers.get(result.voucherId() != null ? result.voucherId() : created.id());
    }
}
