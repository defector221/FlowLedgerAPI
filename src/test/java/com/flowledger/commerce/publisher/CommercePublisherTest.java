package com.flowledger.commerce.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowledger.commerce.integration.entity.MerchantIntegrationProfile;
import com.flowledger.commerce.integration.repository.MerchantIntegrationProfileRepository;
import com.flowledger.commerce.merchant.domain.MerchantType;
import com.flowledger.commerce.onboarding.entity.MerchantOnboarding;
import com.flowledger.commerce.onboarding.repository.MerchantOnboardingRepository;
import com.flowledger.commerce.publisher.entity.MarketplaceProductIndex;
import com.flowledger.commerce.publisher.model.ProductPublishedSnapshot;
import com.flowledger.commerce.publisher.repository.MarketplaceInventoryIndexRepository;
import com.flowledger.commerce.publisher.repository.MarketplaceProductIndexRepository;
import com.flowledger.commerce.publisher.sink.CommercePublishSink;
import com.flowledger.commerce.publisher.sink.MarketplaceIndexPublishSink;
import com.flowledger.commerce.store.entity.StoreCommerceProfile;
import com.flowledger.commerce.store.repository.StoreCommerceProfileRepository;
import com.flowledger.platform.event.DomainEventPublisher;
import com.flowledger.retail.entity.RetailStore;
import com.flowledger.retail.repository.RetailStoreRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CommercePublisherTest {
    @Mock
    private MerchantIntegrationProfileRepository integrationProfiles;
    @Mock
    private MerchantOnboardingRepository onboardingRepository;
    @Mock
    private MarketplaceProductIndexRepository productIndexRepository;
    @Mock
    private MarketplaceInventoryIndexRepository inventoryIndexRepository;
    @Mock
    private MarketplaceIndexPublishSink indexSink;
    @Mock
    private CommercePublishSink openSearchSink;
    @Mock
    private StoreCommerceProfileRepository storeProfiles;
    @Mock
    private RetailStoreRepository retailStores;
    @Mock
    private CatalogAdapter flowLedgerAdapter;
    @Mock
    private DomainEventPublisher events;

    private CommercePublisher publisher;
    private UUID orgId;
    private UUID storeId;
    private UUID productId;

    @BeforeEach
    void setUp() {
        publisher = new CommercePublisher(
                integrationProfiles,
                onboardingRepository,
                productIndexRepository,
                inventoryIndexRepository,
                indexSink,
                List.of(indexSink, openSearchSink),
                storeProfiles,
                retailStores,
                List.of(flowLedgerAdapter),
                new ObjectMapper(),
                events);
        orgId = UUID.randomUUID();
        storeId = UUID.randomUUID();
        productId = UUID.randomUUID();
        when(flowLedgerAdapter.merchantType()).thenReturn(MerchantType.FLOWLEDGER);
    }

    @Test
    void publishCatalogItemFansOutToSinksForPublishedStores() {
        MerchantIntegrationProfile integration = new MerchantIntegrationProfile();
        integration.setMerchantType(MerchantType.FLOWLEDGER);
        when(integrationProfiles.findByOrganizationId(orgId)).thenReturn(Optional.of(integration));

        StoreCommerceProfile profile = new StoreCommerceProfile();
        profile.setOrganizationId(orgId);
        profile.setStoreId(storeId);
        profile.publishStoreToMarketplace();
        profile.setPublishProducts(true);
        profile.setPublishPrices(true);
        when(storeProfiles.findByOrganizationIdAndPublishedToMarketplaceTrue(orgId)).thenReturn(List.of(profile));

        CatalogAdapter.CatalogItemSnapshot item = new CatalogAdapter.CatalogItemSnapshot(
                productId,
                "SKU-1",
                "Milk",
                "Fresh milk",
                BigDecimal.TEN,
                BigDecimal.valueOf(12),
                "123",
                "123",
                "Brand",
                null,
                null,
                "INR",
                List.of(),
                1L);
        when(flowLedgerAdapter.loadItem(integration, profile, productId)).thenReturn(Optional.of(item));
        when(productIndexRepository.findByStoreIdAndProductId(storeId, productId)).thenReturn(Optional.empty());

        publisher.publishCatalogItem(orgId, productId, null);

        ArgumentCaptor<ProductPublishedSnapshot> captor = ArgumentCaptor.forClass(ProductPublishedSnapshot.class);
        verify(indexSink).onProductPublished(captor.capture());
        ProductPublishedSnapshot saved = captor.getValue();
        assertEquals(storeId, saved.storeId());
        assertEquals(productId, saved.productId());
        assertEquals("SKU-1", saved.sku());
        verify(openSearchSink).onProductPublished(any());
        verify(events).publish(any());
    }

    @Test
    void publishCatalogItemSkipsWhenNoPublishedStores() {
        when(integrationProfiles.findByOrganizationId(orgId)).thenReturn(Optional.of(new MerchantIntegrationProfile()));
        when(storeProfiles.findByOrganizationIdAndPublishedToMarketplaceTrue(orgId)).thenReturn(List.of());

        publisher.publishCatalogItem(orgId, productId, null);

        verify(indexSink, never()).onProductPublished(any());
    }

    @Test
    void publishStoreFansOutStoreSnapshotAndProducts() {
        StoreCommerceProfile profile = new StoreCommerceProfile();
        profile.setOrganizationId(orgId);
        profile.setStoreId(storeId);
        profile.publishStoreToMarketplace();
        profile.setPublishProducts(true);

        MerchantOnboarding onboarding = new MerchantOnboarding();
        onboarding.setOrganizationId(orgId);
        onboarding.advanceTo(com.flowledger.commerce.onboarding.domain.MerchantOnboardingState.LIVE);
        when(onboardingRepository.findByOrganizationId(orgId)).thenReturn(Optional.of(onboarding));

        MerchantIntegrationProfile integration = new MerchantIntegrationProfile();
        integration.setMerchantType(MerchantType.FLOWLEDGER);
        when(integrationProfiles.findByOrganizationId(orgId)).thenReturn(Optional.of(integration));

        RetailStore store = new RetailStore();
        store.setId(storeId);
        store.setName("Main Store");
        store.setCity("Mumbai");
        when(retailStores.findById(storeId)).thenReturn(Optional.of(store));

        CatalogAdapter.CatalogItemSnapshot item = new CatalogAdapter.CatalogItemSnapshot(
                productId, "SKU-1", "Milk", null, BigDecimal.ONE, BigDecimal.ONE);
        when(flowLedgerAdapter.loadCatalog(integration, profile)).thenReturn(List.of(item));
        when(productIndexRepository.findByStoreIdAndProductId(storeId, productId)).thenReturn(Optional.empty());

        int count = publisher.publishStore(profile, UUID.randomUUID());
        assertEquals(1, count);
        verify(openSearchSink).onStorePublished(any());
        verify(indexSink).onProductPublished(any());
    }
}
