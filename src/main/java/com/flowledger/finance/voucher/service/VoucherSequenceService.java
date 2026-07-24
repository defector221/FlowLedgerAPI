package com.flowledger.finance.voucher.service;

import com.flowledger.common.util.FinancialYearUtil;
import com.flowledger.finance.voucher.domain.VoucherType;
import com.flowledger.finance.voucher.entity.VoucherSequence;
import com.flowledger.finance.voucher.repository.VoucherSequenceRepository;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VoucherSequenceService {
    private final VoucherSequenceRepository sequences;

    public VoucherSequenceService(VoucherSequenceRepository sequences) {
        this.sequences = sequences;
    }

    @Transactional
    public String nextNumber(UUID org, UUID branchId, VoucherType type, String fyStart, LocalDate date) {
        String fy = FinancialYearUtil.financialYear(date, fyStart);
        String typeKey = type.name();
        String prefix = prefixFor(type);
        VoucherSequence sequence = sequences
                .findLocked(org, typeKey, fy, branchId)
                .orElseGet(() -> {
                    VoucherSequence created = new VoucherSequence();
                    created.setOrganizationId(org);
                    created.setBranchId(branchId);
                    created.setVoucherType(typeKey);
                    created.setFinancialYear(fy);
                    created.setPrefix(prefix);
                    created.setNextNumber(1);
                    return sequences.saveAndFlush(created);
                });
        long next = sequence.getNextNumber();
        sequence.setNextNumber(next + 1);
        return String.format(Locale.ROOT, "%s/%s/%06d", sequence.getPrefix(), fy, next);
    }

    private static String prefixFor(VoucherType type) {
        return switch (type) {
            case SALES -> "SV";
            case PURCHASE -> "PV";
            case PAYMENT -> "PAY";
            case RECEIPT -> "RCT";
            case JOURNAL -> "JV";
            case CONTRA -> "CV";
            case DEBIT_NOTE -> "DN";
            case CREDIT_NOTE -> "CN";
            case STOCK_TRANSFER -> "ST";
            case STOCK_ADJUSTMENT -> "SA";
            case PRODUCTION_ISSUE -> "PI";
            case PRODUCTION_RECEIPT -> "PR";
            case PAYROLL -> "PAYR";
            case ASSET_PURCHASE -> "AP";
            case DEPRECIATION -> "DEP";
            case EXPENSE -> "EXP";
            case OPENING_BALANCE -> "OB";
        };
    }
}
