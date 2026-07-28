package com.flowledger.commerce.scheduler;

import com.flowledger.commerce.integration.entity.MerchantIntegrationProfile;
import com.flowledger.commerce.integration.repository.MerchantIntegrationProfileRepository;
import com.flowledger.commerce.merchant.domain.MerchantType;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class MerchantIntegrationHealthScheduler {
    private static final Logger log = LoggerFactory.getLogger(MerchantIntegrationHealthScheduler.class);

    private final MerchantIntegrationProfileRepository integrationProfiles;

    public MerchantIntegrationHealthScheduler(MerchantIntegrationProfileRepository integrationProfiles) {
        this.integrationProfiles = integrationProfiles;
    }

    @Scheduled(fixedDelayString = "${flowledger.commerce.health-check-interval-ms:300000}")
    @Transactional
    public void refreshIntegrationHealth() {
        OffsetDateTime now = OffsetDateTime.now();
        for (MerchantIntegrationProfile profile : integrationProfiles.findAll()) {
            profile.setHealthStatus(resolveHealth(profile, now));
            profile.setLastHealthCheckAt(now);
            integrationProfiles.save(profile);
        }
        log.debug("Commerce integration health check completed for {} profiles", integrationProfiles.count());
    }

    static String resolveHealth(MerchantIntegrationProfile profile, OffsetDateTime now) {
        if (!"ACTIVE".equals(profile.getStatus())) {
            return "INACTIVE";
        }
        if (profile.getMerchantType() == MerchantType.FLOWLEDGER) {
            return "HEALTHY";
        }
        OffsetDateTime lastSync = profile.getLastSyncAt();
        if (lastSync == null) {
            return "UNKNOWN";
        }
        long hoursSinceSync = ChronoUnit.HOURS.between(lastSync, now);
        if (hoursSinceSync <= 24) {
            return "HEALTHY";
        }
        if (hoursSinceSync <= 72) {
            return "DEGRADED";
        }
        return "UNHEALTHY";
    }
}
