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
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MarketplaceSyncService {
    public static final String JOB_STORE_PUBLISH = "STORE_PUBLISH";
    public static final String JOB_STORE_UNPUBLISH = "STORE_UNPUBLISH";
    public static final String JOB_CAPABILITIES_SYNC = "CAPABILITIES_SYNC";

    private final StoreCommerceProfileRepository storeProfiles;
    private final CommercePublisher publisher;
    private final MarketplaceSyncJobRepository jobs;
    private final CommerceProperties properties;
    private final MarketplaceSyncTrigger syncTrigger;

    public MarketplaceSyncService(
            StoreCommerceProfileRepository storeProfiles,
            CommercePublisher publisher,
            MarketplaceSyncJobRepository jobs,
            CommerceProperties properties,
            @Lazy MarketplaceSyncTrigger syncTrigger) {
        this.storeProfiles = storeProfiles;
        this.publisher = publisher;
        this.jobs = jobs;
        this.properties = properties;
        this.syncTrigger = syncTrigger;
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

    public UUID enqueueStorePublish(UUID orgId, UUID storeId) {
        return enqueueAdminJob(JOB_STORE_PUBLISH, "STORE", orgId, storeId, storeId, SyncMode.FULL);
    }

    public UUID enqueueStoreUnpublish(UUID orgId, UUID storeId) {
        return enqueueAdminJob(JOB_STORE_UNPUBLISH, "STORE", orgId, storeId, storeId, SyncMode.FULL);
    }

    public UUID enqueueCapabilitiesSync(UUID orgId) {
        return enqueueAdminJob(JOB_CAPABILITIES_SYNC, "ORG", orgId, orgId, null, SyncMode.FULL);
    }

    public void enqueue(String jobType, String entityType, UUID orgId, UUID entityId, UUID storeId, SyncMode mode) {
        String key = jobType + ":" + entityType + ":" + entityId + ":" + storeId + ":" + mode.name();
        if (jobs.findByIdempotencyKey(key).isPresent()) {
            return;
        }
        saveJob(jobType, entityType, orgId, entityId, storeId, mode, key);
    }

    public UUID enqueueAdminJob(
            String jobType, String entityType, UUID orgId, UUID entityId, UUID storeId, SyncMode mode) {
        String key = jobType + ":" + entityType + ":" + entityId + ":" + storeId + ":" + mode.name() + ":"
                + UUID.randomUUID();
        UUID jobId = saveJob(jobType, entityType, orgId, entityId, storeId, mode, key);
        syncTrigger.drainSoon();
        return jobId;
    }

    private UUID saveJob(
            String jobType, String entityType, UUID orgId, UUID entityId, UUID storeId, SyncMode mode, String key) {
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
        return jobs.save(job).getId();
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
        if (JOB_CAPABILITIES_SYNC.equals(job.getJobType()) && job.getOrganizationId() != null) {
            for (StoreCommerceProfile profile :
                    storeProfiles.findByOrganizationIdAndPublishedToMarketplaceTrue(job.getOrganizationId())) {
                syncStore(profile.getStoreId(), SyncMode.FULL, actorId);
            }
            job.setResultDetail("Capabilities propagated to published stores");
            return;
        }
        if (JOB_STORE_PUBLISH.equals(job.getJobType()) && job.getStoreId() != null) {
            StoreCommerceProfile profile = storeProfiles
                    .findByStoreId(job.getStoreId())
                    .orElseThrow(() -> new ResourceNotFoundException("Store commerce profile not found"));
            profile.publishStoreToMarketplace();
            storeProfiles.save(profile);
            int count = publisher.publishStore(profile, actorId);
            job.setResultDetail(String.valueOf(count));
            return;
        }
        if (JOB_STORE_UNPUBLISH.equals(job.getJobType()) && job.getStoreId() != null) {
            StoreCommerceProfile profile = storeProfiles
                    .findByStoreId(job.getStoreId())
                    .orElseThrow(() -> new ResourceNotFoundException("Store commerce profile not found"));
            publisher.unpublishStore(profile, actorId);
            storeProfiles.save(profile);
            job.setResultDetail("Store unpublished");
            return;
        }
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
