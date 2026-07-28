package com.flowledger.commerce.marketplace;

import com.flowledger.commerce.config.CommerceProperties;
import com.flowledger.commerce.marketplace.domain.SyncMode;
import com.flowledger.commerce.marketplace.sync.MarketplaceSyncJob;
import com.flowledger.commerce.marketplace.sync.MarketplaceSyncJobRepository;
import com.flowledger.commerce.publisher.CommercePublisher;
import com.flowledger.commerce.store.entity.StoreCommerceProfile;
import com.flowledger.commerce.store.repository.StoreCommerceProfileRepository;
import com.flowledger.common.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MarketplaceSyncService {
    private final StoreCommerceProfileRepository storeProfiles;
    private final CommercePublisher publisher;
    private final MarketplaceSyncJobRepository jobs;
    private final CommerceProperties properties;

    public MarketplaceSyncService(
            StoreCommerceProfileRepository storeProfiles,
            CommercePublisher publisher,
            MarketplaceSyncJobRepository jobs,
            CommerceProperties properties) {
        this.storeProfiles = storeProfiles;
        this.publisher = publisher;
        this.jobs = jobs;
        this.properties = properties;
    }

    public int syncStore(UUID storeId, SyncMode mode, UUID actorId) {
        StoreCommerceProfile profile = storeProfiles
                .findByStoreId(storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Store commerce profile not found"));
        if (!profile.isCommerceEnabled() || !profile.isPublishedToMarketplace()) {
            return 0;
        }
        if (mode == SyncMode.FULL_REPUBLISH) {
            publisher.unpublishStore(profile, actorId);
            profile.publishStoreToMarketplace();
            storeProfiles.save(profile);
        }
        return publisher.publishStore(profile, actorId);
    }

    public void syncProduct(UUID storeId, UUID productId, UUID actorId) {
        StoreCommerceProfile profile = requirePublishedProfile(storeId);
        publisher.publishCatalogItem(profile.getOrganizationId(), productId, actorId);
    }

    public void syncPrice(UUID storeId, UUID productId, UUID actorId) {
        syncProduct(storeId, productId, actorId);
    }

    public void syncInventory(UUID storeId, UUID productId, BigDecimal qty, UUID actorId) {
        StoreCommerceProfile profile = requirePublishedProfile(storeId);
        publisher.publishInventoryItem(profile.getOrganizationId(), storeId, productId, qty, actorId);
    }

    public void syncCategory(UUID organizationId, UUID categoryId, UUID actorId) {
        for (StoreCommerceProfile profile : storeProfiles.findByOrganizationIdAndPublishedToMarketplaceTrue(organizationId)) {
            publisher.publishStore(profile, actorId);
        }
    }

    public void enqueue(String jobType, String entityType, UUID orgId, UUID entityId, UUID storeId, SyncMode mode) {
        String key = jobType + ":" + entityType + ":" + entityId + ":" + storeId + ":" + mode.name();
        if (jobs.findByIdempotencyKey(key).isPresent()) {
            return;
        }
        MarketplaceSyncJob job = new MarketplaceSyncJob();
        job.setJobType(jobType);
        job.setEntityType(entityType);
        job.setOrganizationId(orgId);
        job.setEntityId(entityId);
        job.setStoreId(storeId);
        job.setSyncMode(mode.name());
        job.setStatus("PENDING");
        job.setMaxAttempts(properties.getMarketplace().getSync().getMaxRetries());
        job.setIdempotencyKey(key);
        jobs.save(job);
    }

    public void processPendingJobs() {
        int batch = properties.getMarketplace().getSync().getBatchSize();
        for (MarketplaceSyncJob job : jobs.findDueJobs(OffsetDateTime.now())) {
            if (batch-- <= 0) {
                break;
            }
            try {
                execute(job);
                job.setStatus("COMPLETED");
                job.setCompletedAt(OffsetDateTime.now());
            } catch (Exception ex) {
                job.setAttempts(job.getAttempts() + 1);
                job.setLastError(ex.getMessage());
                job.setStatus(job.getAttempts() >= job.getMaxAttempts() ? "FAILED" : "PENDING");
                job.setScheduledAt(OffsetDateTime.now().plusSeconds((long) Math.pow(2, job.getAttempts()) * 30));
            }
            jobs.save(job);
        }
    }

    private void execute(MarketplaceSyncJob job) {
        SyncMode mode = SyncMode.valueOf(job.getSyncMode());
        UUID actorId = null;
        if ("ORG".equals(job.getEntityType()) && job.getOrganizationId() != null) {
            for (StoreCommerceProfile profile :
                    storeProfiles.findByOrganizationIdAndPublishedToMarketplaceTrue(job.getOrganizationId())) {
                syncStore(profile.getStoreId(), mode, actorId);
            }
            return;
        }
        if (job.getStoreId() != null && "STORE".equals(job.getEntityType())) {
            syncStore(job.getStoreId(), mode, actorId);
        } else if (job.getEntityId() != null && job.getStoreId() != null) {
            syncProduct(job.getStoreId(), job.getEntityId(), actorId);
        }
    }

    private StoreCommerceProfile requirePublishedProfile(UUID storeId) {
        StoreCommerceProfile profile = storeProfiles
                .findByStoreId(storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Store commerce profile not found"));
        if (!profile.isPublishedToMarketplace()) {
            throw new ResourceNotFoundException("Store is not published to marketplace");
        }
        return profile;
    }
}
