package com.flowledger.commerce.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.flowledger.commerce.integration.entity.MerchantIntegrationProfile;
import com.flowledger.commerce.merchant.domain.MerchantType;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class MerchantIntegrationHealthSchedulerTest {
    @Test
    void flowLedgerMerchantsAreHealthyWhenActive() {
        MerchantIntegrationProfile profile = new MerchantIntegrationProfile();
        profile.setMerchantType(MerchantType.FLOWLEDGER);
        profile.setStatus("ACTIVE");
        assertEquals("HEALTHY", MerchantIntegrationHealthScheduler.resolveHealth(profile, OffsetDateTime.now()));
    }

    @Test
    void partnerWithoutSyncIsUnknown() {
        MerchantIntegrationProfile profile = new MerchantIntegrationProfile();
        profile.setMerchantType(MerchantType.PARTNER);
        profile.setStatus("ACTIVE");
        assertEquals("UNKNOWN", MerchantIntegrationHealthScheduler.resolveHealth(profile, OffsetDateTime.now()));
    }

    @Test
    void partnerWithRecentSyncIsHealthy() {
        MerchantIntegrationProfile profile = new MerchantIntegrationProfile();
        profile.setMerchantType(MerchantType.PARTNER);
        profile.setStatus("ACTIVE");
        profile.setLastSyncAt(OffsetDateTime.now().minusHours(2));
        assertEquals("HEALTHY", MerchantIntegrationHealthScheduler.resolveHealth(profile, OffsetDateTime.now()));
    }
}
