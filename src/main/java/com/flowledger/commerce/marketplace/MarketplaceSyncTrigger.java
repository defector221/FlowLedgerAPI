package com.flowledger.commerce.marketplace;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class MarketplaceSyncTrigger {
    private final MarketplaceSyncService syncService;

    public MarketplaceSyncTrigger(MarketplaceSyncService syncService) {
        this.syncService = syncService;
    }

    @Async
    public void drainSoon() {
        syncService.processPendingJobs();
    }
}
