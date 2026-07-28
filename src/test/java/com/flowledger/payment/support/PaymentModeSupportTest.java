package com.flowledger.payment.support;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PaymentModeSupportTest {

    @Test
    void mapsCardAlias() {
        assertEquals("CREDIT_CARD", PaymentModeSupport.normalize("CARD"));
    }

    @Test
    void passesThroughLedgerModes() {
        assertEquals("UPI", PaymentModeSupport.normalize("UPI"));
        assertEquals("CREDIT_CARD", PaymentModeSupport.normalize("CREDIT_CARD"));
    }

    @Test
    void mapsWalletAlias() {
        assertEquals("PAYMENT_GATEWAY", PaymentModeSupport.normalize("WALLET"));
    }
}
