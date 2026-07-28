package com.flowledger.commerce.marketplace;

import com.flowledger.commerce.dto.CommerceDtos;
import com.flowledger.commerce.marketplace.domain.MarketplaceProduct;
import com.flowledger.commerce.marketplace.domain.MarketplaceStore;
import com.flowledger.commerce.marketplace.mapper.MarketplaceIndexMapper;
import com.flowledger.commerce.marketplace.search.GeoCriteria;
import com.flowledger.commerce.marketplace.search.MarketplaceSearchBackend;
import com.flowledger.commerce.marketplace.search.PostgresMarketplaceSearchBackend;
import com.flowledger.commerce.publisher.entity.MarketplaceProductIndex;
import com.flowledger.commerce.publisher.repository.MarketplaceProductIndexRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MarketplaceBarcodeService {
    private final MarketplaceSearchBackend searchBackend;
    private final MarketplaceProductIndexRepository productIndexRepository;
    private final MarketplaceIndexMapper mapper;

    public MarketplaceBarcodeService(
            PostgresMarketplaceSearchBackend searchBackend,
            MarketplaceProductIndexRepository productIndexRepository,
            MarketplaceIndexMapper mapper) {
        this.searchBackend = searchBackend;
        this.productIndexRepository = productIndexRepository;
        this.mapper = mapper;
    }

    public CommerceDtos.MarketplaceBarcodeLookupResponse lookup(String barcode, BigDecimal lat, BigDecimal lng, BigDecimal radiusKm) {
        List<MarketplaceProductIndex> matches = productIndexRepository.findByBarcodeAndPublishedTrue(barcode);
        if (matches.isEmpty()) {
            return new CommerceDtos.MarketplaceBarcodeLookupResponse(null, List.of());
        }
        MarketplaceProductIndex first = matches.get(0);
        MarketplaceProduct product = mapper.toProduct(first);
        GeoCriteria geo = new GeoCriteria(lat, lng, radiusKm);
        List<MarketplaceStore> stores = searchBackend.findStoresNearProduct(first.getProductId(), geo);
        List<CommerceDtos.MarketplaceStoreAvailability> availability = stores.stream()
                .map(s -> new CommerceDtos.MarketplaceStoreAvailability(
                        s.storeId(),
                        s.name(),
                        s.city(),
                        s.distanceKm(),
                        matches.stream()
                                .filter(p -> p.getStoreId().equals(s.storeId()))
                                .findFirst()
                                .map(MarketplaceProductIndex::getPrice)
                                .orElse(null),
                        matches.stream()
                                .filter(p -> p.getStoreId().equals(s.storeId()))
                                .findFirst()
                                .map(MarketplaceProductIndex::getInventoryQty)
                                .orElse(null)))
                .toList();
        return new CommerceDtos.MarketplaceBarcodeLookupResponse(toProductDto(product), availability);
    }

    private static CommerceDtos.MarketplaceProductResponse toProductDto(MarketplaceProduct p) {
        return new CommerceDtos.MarketplaceProductResponse(
                p.id(),
                p.storeId(),
                p.productId(),
                p.sku(),
                p.barcode(),
                p.gtin(),
                p.name(),
                p.description(),
                p.brand(),
                p.categoryId(),
                p.categoryName(),
                p.price(),
                p.currency(),
                p.inventoryQty(),
                p.imageUrls());
    }
}
