package com.flowledger.commerce.marketplace.scheduler;

import com.flowledger.commerce.marketplace.MarketplaceSyncService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MarketplaceSyncScheduler {
    private final MarketplaceSyncService syncService;

    public MarketplaceSyncScheduler(MarketplaceSyncService syncService) {
        this.syncService = syncService;
    }

    @Scheduled(fixedDelayString = "${flowledger.commerce.marketplace.sync.poll-interval-ms:60000}")
    public void drainSyncJobs() {
        syncService.processPendingJobs();
    }
}
