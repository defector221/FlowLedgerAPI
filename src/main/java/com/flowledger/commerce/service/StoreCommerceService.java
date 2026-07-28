package com.flowledger.commerce.service;

import com.flowledger.commerce.audit.CommerceAuditService;
import com.flowledger.commerce.config.CommerceModuleGuard;
import com.flowledger.commerce.dto.CommerceDtos;
import com.flowledger.commerce.events.StoreCommerceEnabledEvent;
import com.flowledger.commerce.mapper.CommerceMapper;
import com.flowledger.commerce.onboarding.MerchantOnboardingService;
import com.flowledger.commerce.marketplace.MarketplaceSyncService;
import com.flowledger.commerce.store.entity.StoreCommerceProfile;
import com.flowledger.commerce.store.repository.StoreCommerceProfileRepository;
import com.flowledger.common.exception.ResourceNotFoundException;
import com.flowledger.common.tenant.TenantContext;
import com.flowledger.platform.event.DomainEventPublisher;
import com.flowledger.retail.entity.RetailStore;
import com.flowledger.retail.repository.RetailStoreRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class StoreCommerceService {
    private final CommerceModuleGuard guard;
    private final StoreCommerceProfileRepository profiles;
    private final RetailStoreRepository retailStores;
    private final MarketplaceSyncService marketplaceSync;
    private final MerchantOnboardingService onboardingService;
    private final CommerceAuditService audit;
    private final CommerceMapper mapper;
    private final DomainEventPublisher events;

    public StoreCommerceService(
            CommerceModuleGuard guard,
            StoreCommerceProfileRepository profiles,
            RetailStoreRepository retailStores,
            MarketplaceSyncService marketplaceSync,
            MerchantOnboardingService onboardingService,
            CommerceAuditService audit,
            CommerceMapper mapper,
            DomainEventPublisher events) {
        this.guard = guard;
        this.profiles = profiles;
        this.retailStores = retailStores;
        this.marketplaceSync = marketplaceSync;
        this.onboardingService = onboardingService;
        this.audit = audit;
        this.mapper = mapper;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public CommerceDtos.StoreCommerceResponse get(UUID storeId) {
        UUID orgId = guard.ensureEnabled();
        assertStoreOwnership(storeId, orgId);
        return mapper.toStoreCommerceResponse(getOrCreateProfile(storeId, orgId));
    }

    public CommerceDtos.StoreCommerceUpdateResponse update(UUID storeId, CommerceDtos.UpdateStoreCommerceRequest request) {
        UUID orgId = guard.ensureEnabled();
        UUID actorId = TenantContext.userId().orElse(null);
        assertStoreOwnership(storeId, orgId);
        StoreCommerceProfile profile = getOrCreateProfile(storeId, orgId);
        boolean wasEnabled = profile.isCommerceEnabled();
        boolean wasPublished = profile.isPublishedToMarketplace();
        applyUpdate(profile, request);
        profiles.save(profile);
        if (profile.isCommerceEnabled() && !wasEnabled) {
            events.publish(new StoreCommerceEnabledEvent(this, orgId, actorId, storeId));
        }
        UUID syncJobId = null;
        if (profile.isCommerceEnabled()
                && profile.isPublishedToMarketplace()
                && (!wasPublished || publishFlagsChanged(request))) {
            syncJobId = marketplaceSync.enqueueStorePublish(orgId, storeId);
        }
        onboardingService.ensureOnboarding(orgId);
        audit.log(orgId, "StoreCommerceProfile", profile.getId(), "UPDATE", null, null);
        return new CommerceDtos.StoreCommerceUpdateResponse(mapper.toStoreCommerceResponse(profile), syncJobId);
    }

    public CommerceDtos.PublishResultResponse publish(UUID storeId) {
        UUID orgId = guard.ensureEnabled();
        assertStoreOwnership(storeId, orgId);
        StoreCommerceProfile profile = getOrCreateProfile(storeId, orgId);
        profile.publishStoreToMarketplace();
        profiles.save(profile);
        onboardingService.ensureOnboarding(orgId);
        UUID jobId = marketplaceSync.enqueueStorePublish(orgId, storeId);
        audit.log(orgId, "StoreCommerceProfile", profile.getId(), "PUBLISH_QUEUED", null, jobId.toString());
        return new CommerceDtos.PublishResultResponse(storeId, null, true, jobId, "QUEUED");
    }

    public CommerceDtos.PublishResultResponse unpublish(UUID storeId) {
        UUID orgId = guard.ensureEnabled();
        assertStoreOwnership(storeId, orgId);
        StoreCommerceProfile profile = getOrCreateProfile(storeId, orgId);
        UUID jobId = marketplaceSync.enqueueStoreUnpublish(orgId, storeId);
        audit.log(orgId, "StoreCommerceProfile", profile.getId(), "UNPUBLISH_QUEUED", null, jobId.toString());
        return new CommerceDtos.PublishResultResponse(storeId, null, false, jobId, "QUEUED");
    }

    private StoreCommerceProfile getOrCreateProfile(UUID storeId, UUID orgId) {
        return profiles.findByStoreIdAndOrganizationId(storeId, orgId).orElseGet(() -> {
            StoreCommerceProfile profile = new StoreCommerceProfile();
            profile.setOrganizationId(orgId);
            profile.setStoreId(storeId);
            return profiles.save(profile);
        });
    }

    private void assertStoreOwnership(UUID storeId, UUID orgId) {
        RetailStore store = retailStores
                .findById(storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Store not found"));
        if (!store.getOrganizationId().equals(orgId)) {
            throw new ResourceNotFoundException("Store not found");
        }
    }

    private static void applyUpdate(StoreCommerceProfile profile, CommerceDtos.UpdateStoreCommerceRequest request) {
        if (request.commerceEnabled() != null) {
            if (request.commerceEnabled()) profile.enableCommerce();
            else profile.disableCommerce();
        }
        if (request.acceptOnlineOrders() != null) profile.setAcceptOnlineOrders(request.acceptOnlineOrders());
        if (request.supportsDelivery() != null) profile.setSupportsDelivery(request.supportsDelivery());
        if (request.supportsPickup() != null) profile.setSupportsPickup(request.supportsPickup());
        if (request.supportsClickCollect() != null) profile.setSupportsClickCollect(request.supportsClickCollect());
        if (request.supportsScanAndGo() != null) profile.setSupportsScanAndGo(request.supportsScanAndGo());
        if (request.publishedToMarketplace() != null) {
            if (request.publishedToMarketplace()) profile.publishStoreToMarketplace();
            else profile.unpublishStoreFromMarketplace();
        }
        if (request.publishProducts() != null) profile.setPublishProducts(request.publishProducts());
        if (request.publishInventory() != null) profile.setPublishInventory(request.publishInventory());
        if (request.publishPrices() != null) profile.setPublishPrices(request.publishPrices());
        if (request.visibility() != null) profile.setVisibility(request.visibility());
        if (request.discoveryRadius() != null) profile.setDiscoveryRadius(request.discoveryRadius());
        if (request.status() != null) profile.setStatus(request.status());
    }

    private static boolean publishFlagsChanged(CommerceDtos.UpdateStoreCommerceRequest request) {
        return request.publishProducts() != null
                || request.publishInventory() != null
                || request.publishPrices() != null
                || request.publishedToMarketplace() != null;
    }
}
