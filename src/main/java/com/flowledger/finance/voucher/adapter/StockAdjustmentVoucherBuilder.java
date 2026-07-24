package com.flowledger.finance.voucher.adapter;

import static com.flowledger.finance.voucher.adapter.VoucherLineSupport.*;

import com.flowledger.accounting.domain.SystemAccountKey;
import com.flowledger.accounting.util.AccountingMoney;
import com.flowledger.finance.voucher.domain.VoucherType;
import com.flowledger.finance.voucher.dto.VoucherDtos.VoucherLineRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Builds STOCK_ADJUSTMENT vouchers when inventory value is known.
 * Returns empty when INVENTORY/COGS (or OTHER_INCOME) system accounts are missing.
 */
@Component
public class StockAdjustmentVoucherBuilder {
    public static final String REFERENCE_TYPE = "STOCK_ADJUSTMENT";

    private static final Logger log = LoggerFactory.getLogger(StockAdjustmentVoucherBuilder.class);

    private final SystemAccountLookup accounts;

    public StockAdjustmentVoucherBuilder(SystemAccountLookup accounts) {
        this.accounts = accounts;
    }

    /**
     * @param quantityDelta signed qty change (positive = stock increase, negative = decrease)
     * @param amount absolute inventory value of the movement
     */
    public Optional<BuiltDocumentVoucher> build(
            UUID orgId,
            UUID referenceId,
            LocalDate date,
            BigDecimal quantityDelta,
            BigDecimal amount,
            String narration) {
        if (orgId == null || referenceId == null) {
            return Optional.empty();
        }
        if (amount == null || !AccountingMoney.isPositive(amount)) {
            log.debug("Skipping stock adjustment voucher: amount unknown or zero (ref={})", referenceId);
            return Optional.empty();
        }
        if (quantityDelta == null || quantityDelta.signum() == 0) {
            return Optional.empty();
        }

        accounts.ensureInitialized(orgId);
        Optional<UUID> inventory = accounts.find(orgId, SystemAccountKey.INVENTORY);
        if (inventory.isEmpty()) {
            log.warn("Skipping stock adjustment voucher: INVENTORY system account missing (org={})", orgId);
            return Optional.empty();
        }

        List<VoucherLineRequest> lines = new ArrayList<>();
        String label = narration != null && !narration.isBlank() ? narration : "Stock adjustment";

        if (quantityDelta.signum() > 0) {
            Optional<UUID> income = accounts.find(orgId, SystemAccountKey.OTHER_INCOME);
            if (income.isEmpty()) {
                log.warn("Skipping stock adjustment voucher: OTHER_INCOME system account missing (org={})", orgId);
                return Optional.empty();
            }
            lines.add(debit(inventory.get(), amount, label));
            lines.add(credit(income.get(), amount, label));
        } else {
            Optional<UUID> cogs = accounts.find(orgId, SystemAccountKey.COGS);
            if (cogs.isEmpty()) {
                cogs = accounts.find(orgId, SystemAccountKey.OPERATING_EXPENSES);
            }
            if (cogs.isEmpty()) {
                log.warn("Skipping stock adjustment voucher: COGS/OPERATING_EXPENSES missing (org={})", orgId);
                return Optional.empty();
            }
            lines.add(debit(cogs.get(), amount, label));
            lines.add(credit(inventory.get(), amount, label));
        }

        return Optional.of(new BuiltDocumentVoucher(
                VoucherType.STOCK_ADJUSTMENT,
                date != null ? date : LocalDate.now(),
                null,
                "INR",
                BigDecimal.ONE,
                REFERENCE_TYPE,
                referenceId,
                label,
                lines));
    }
}
