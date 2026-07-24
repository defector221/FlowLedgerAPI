package com.flowledger.finance.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "flowledger.finance")
public class FinanceProperties {
    /**
     * When true (default), sales/purchase/payment documents post through the voucher engine.
     * When false, fall back to {@code AccountingPostingService} document posting methods.
     */
    private boolean voucherEngineEnabled = true;

    public boolean isVoucherEngineEnabled() {
        return voucherEngineEnabled;
    }

    public void setVoucherEngineEnabled(boolean voucherEngineEnabled) {
        this.voucherEngineEnabled = voucherEngineEnabled;
    }
}
