package com.flowledger.commerce.api;

import com.flowledger.commerce.dto.CommerceDtos;
import com.flowledger.commerce.marketplace.MarketplaceBarcodeService;
import com.flowledger.commerce.marketplace.MarketplaceSearchService;
import com.flowledger.commerce.marketplace.domain.MarketplaceBrand;
import com.flowledger.commerce.marketplace.domain.MarketplaceCategory;
import com.flowledger.commerce.marketplace.domain.MarketplaceProduct;
import com.flowledger.commerce.marketplace.domain.MarketplaceStore;
import com.flowledger.commerce.marketplace.search.ProductSearchCriteria;
import com.flowledger.commerce.marketplace.search.StoreSearchCriteria;
import com.flowledger.common.dto.ApiResponse;
import com.flowledger.common.dto.PageResponse;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/commerce/marketplace")
public class MarketplaceController {
    private final MarketplaceSearchService searchService;
    private final MarketplaceBarcodeService barcodeService;

    public MarketplaceController(MarketplaceSearchService searchService, MarketplaceBarcodeService barcodeService) {
        this.searchService = searchService;
        this.barcodeService = barcodeService;
    }

    @GetMapping("/stores")
    public ApiResponse<PageResponse<CommerceDtos.MarketplaceStoreResponse>> searchStores(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String pincode,
            @RequestParam(required = false) BigDecimal lat,
            @RequestParam(required = false) BigDecimal lng,
            @RequestParam(required = false) BigDecimal radiusKm,
            @PageableDefault(size = 20) Pageable pageable) {
        StoreSearchCriteria criteria = new StoreSearchCriteria(q, city, pincode, lat, lng, radiusKm, null, null, null);
        PageResponse<MarketplaceStore> page = searchService.searchStores(criteria, pageable);
        return ApiResponse.of(mapStorePage(page));
    }

    @GetMapping("/stores/{storeId}")
    public ApiResponse<CommerceDtos.MarketplaceStoreResponse> getStore(@PathVariable UUID storeId) {
        return ApiResponse.of(toStoreDto(searchService.getStore(storeId)));
    }

    @GetMapping("/products")
    public ApiResponse<PageResponse<CommerceDtos.MarketplaceProductResponse>> searchProducts(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String barcode,
            @RequestParam(required = false) String sku,
            @RequestParam(required = false) String gtin,
            @RequestParam(required = false) String productCode,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID storeId,
            @PageableDefault(size = 20) Pageable pageable) {
        ProductSearchCriteria criteria =
                new ProductSearchCriteria(q, barcode, sku, gtin, productCode, brand, categoryId, storeId, null, null, null);
        PageResponse<MarketplaceProduct> page = searchService.searchProducts(criteria, pageable);
        return ApiResponse.of(mapProductPage(page));
    }

    @GetMapping("/products/{id}")
    public ApiResponse<CommerceDtos.MarketplaceProductResponse> getProduct(@PathVariable UUID id) {
        return ApiResponse.of(toProductDto(searchService.getProduct(id)));
    }

    @GetMapping("/barcode/{barcode}")
    public ApiResponse<CommerceDtos.MarketplaceBarcodeLookupResponse> barcodeLookup(
            @PathVariable String barcode,
            @RequestParam(required = false) BigDecimal lat,
            @RequestParam(required = false) BigDecimal lng,
            @RequestParam(required = false) BigDecimal radiusKm) {
        return ApiResponse.of(barcodeService.lookup(barcode, lat, lng, radiusKm));
    }

    @GetMapping("/categories")
    public ApiResponse<java.util.List<CommerceDtos.MarketplaceCategoryResponse>> categories() {
        return ApiResponse.of(searchService.listCategories().stream().map(this::toCategoryDto).toList());
    }

    @GetMapping("/brands")
    public ApiResponse<java.util.List<CommerceDtos.MarketplaceBrandResponse>> brands() {
        return ApiResponse.of(searchService.listBrands().stream().map(this::toBrandDto).toList());
    }

    private PageResponse<CommerceDtos.MarketplaceStoreResponse> mapStorePage(PageResponse<MarketplaceStore> page) {
        return new PageResponse<>(
                page.content().stream().map(this::toStoreDto).toList(),
                page.page(),
                page.size(),
                page.totalElements(),
                page.totalPages());
    }

    private PageResponse<CommerceDtos.MarketplaceProductResponse> mapProductPage(PageResponse<MarketplaceProduct> page) {
        return new PageResponse<>(
                page.content().stream().map(this::toProductDto).toList(),
                page.page(),
                page.size(),
                page.totalElements(),
                page.totalPages());
    }

    private CommerceDtos.MarketplaceStoreResponse toStoreDto(MarketplaceStore s) {
        return new CommerceDtos.MarketplaceStoreResponse(
                s.id(),
                s.storeId(),
                s.name(),
                s.city(),
                s.postalCode(),
                s.state(),
                s.country(),
                s.latitude(),
                s.longitude(),
                s.supportsDelivery(),
                s.supportsPickup(),
                s.supportsClickCollect(),
                s.supportsScanAndGo(),
                s.distanceKm());
    }

    private CommerceDtos.MarketplaceProductResponse toProductDto(MarketplaceProduct p) {
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

    private CommerceDtos.MarketplaceCategoryResponse toCategoryDto(MarketplaceCategory c) {
        return new CommerceDtos.MarketplaceCategoryResponse(c.id(), c.categoryId(), c.name(), c.parentId(), c.productCount());
    }

    private CommerceDtos.MarketplaceBrandResponse toBrandDto(MarketplaceBrand b) {
        return new CommerceDtos.MarketplaceBrandResponse(b.id(), b.brandName(), b.productCount());
    }
}
