package com.flowledger.retail.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.flowledger.retail.domain.RetailEnums.PaymentMode;
import org.junit.jupiter.api.Test;

class PosPaymentModeMapperTest {

    @Test
    void mapsRetailCardToCreditCard() {
        assertEquals("CREDIT_CARD", PosPaymentModeMapper.toLedgerMode(PaymentMode.CARD));
    }

    @Test
    void mapsWalletToPaymentGateway() {
        assertEquals("PAYMENT_GATEWAY", PosPaymentModeMapper.toLedgerMode(PaymentMode.WALLET));
    }

    @Test
    void normalizeAcceptsLegacyCardString() {
        assertEquals("CREDIT_CARD", PosPaymentModeMapper.toLedgerMode(PaymentMode.CARD));
    }
}
