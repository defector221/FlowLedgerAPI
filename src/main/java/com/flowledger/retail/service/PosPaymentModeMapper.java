package com.flowledger.retail.service;

import com.flowledger.payment.support.PaymentModeSupport;
import com.flowledger.retail.domain.RetailEnums.PaymentMode;

/** Maps retail POS tender types to ledger {@code payments.payment_mode} values. */
final class PosPaymentModeMapper {

    private PosPaymentModeMapper() {}

    static String toLedgerMode(PaymentMode mode) {
        if (mode == null) {
            return "OTHER";
        }
        return PaymentModeSupport.normalize(
                switch (mode) {
                    case CASH -> "CASH";
                    case UPI -> "UPI";
                    case CARD -> "CARD";
                    case WALLET -> "WALLET";
                    case CREDIT -> "CREDIT";
                });
    }
}
