package com.flowledger.commerce.marketplace.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.flowledger.commerce.config.CommerceProperties;
import com.flowledger.commerce.marketplace.mapper.MarketplaceIndexMapper;
import com.flowledger.commerce.publisher.entity.MarketplaceProductIndex;
import com.flowledger.commerce.publisher.entity.MarketplaceStoreIndex;
import com.flowledger.commerce.publisher.repository.MarketplaceInventoryIndexRepository;
import com.flowledger.commerce.publisher.repository.MarketplaceProductIndexRepository;
import com.flowledger.commerce.publisher.repository.MarketplaceStoreIndexRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class PostgresMarketplaceSearchBackendTest {
    @Mock
    private MarketplaceStoreIndexRepository storeIndexRepository;
    @Mock
    private MarketplaceProductIndexRepository productIndexRepository;
    @Mock
    private MarketplaceInventoryIndexRepository inventoryIndexRepository;

    private PostgresMarketplaceSearchBackend backend;

    @BeforeEach
    void setUp() {
        CommerceProperties properties = new CommerceProperties();
        backend = new PostgresMarketplaceSearchBackend(
                storeIndexRepository,
                productIndexRepository,
                inventoryIndexRepository,
                new MarketplaceIndexMapper(new ObjectMapper()),
                properties);
    }

    @Test
    void searchStoresFiltersByCity() {
        MarketplaceStoreIndex mumbai = store("Mumbai");
        MarketplaceStoreIndex delhi = store("Delhi");
        when(storeIndexRepository.findAll()).thenReturn(List.of(mumbai, delhi));

        var page = backend.searchStores(new StoreSearchCriteria(null, "Mumbai", null, null, null, null, null, null, null), PageRequest.of(0, 10));
        assertEquals(1, page.getTotalElements());
        assertEquals("Mumbai", page.getContent().get(0).city());
    }

    @Test
    void findByBarcodeReturnsPublishedProduct() {
        MarketplaceProductIndex index = new MarketplaceProductIndex();
        index.setPublished(true);
        index.setBarcode("890123");
        index.setName("Tea");
        index.setPayload("{}");
        index.setImageUrls("[]");
        when(productIndexRepository.findFirstByBarcodeAndPublishedTrue("890123")).thenReturn(Optional.of(index));

        assertTrue(backend.findByBarcode("890123").isPresent());
        assertEquals("Tea", backend.findByBarcode("890123").get().name());
    }

    private static MarketplaceStoreIndex store(String city) {
        MarketplaceStoreIndex index = new MarketplaceStoreIndex();
        index.setPublished(true);
        index.setVisibility("PUBLIC");
        index.setCity(city);
        index.setName("Store " + city);
        index.setPayload("{}");
        return index;
    }
}
