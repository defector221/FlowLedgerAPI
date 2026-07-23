package com.flowledger.retail.controller;

import static com.flowledger.retail.dto.RetailDtos.*;

import com.flowledger.retail.service.RetailPricingService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/retail/pricing")
public class RetailPricingController {
    private final RetailPricingService service;

    public RetailPricingController(RetailPricingService service) {
        this.service = service;
    }

    // ------------------------------------------------------------- Price lists
    @GetMapping("/price-lists")
    @PreAuthorize("hasAuthority('RETAIL_VIEW')")
    public List<PriceListResponse> listPriceLists() {
        return service.listPriceLists();
    }

    @GetMapping("/price-lists/{id}")
    @PreAuthorize("hasAuthority('RETAIL_VIEW')")
    public PriceListResponse getPriceList(@PathVariable UUID id) {
        return service.getPriceList(id);
    }

    @PostMapping("/price-lists")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('RETAIL_STORE_MANAGE')")
    public PriceListResponse createPriceList(@Valid @RequestBody PriceListRequest r) {
        return service.createPriceList(r);
    }

    @PutMapping("/price-lists/{id}")
    @PreAuthorize("hasAuthority('RETAIL_STORE_MANAGE')")
    public PriceListResponse updatePriceList(@PathVariable UUID id, @Valid @RequestBody PriceListRequest r) {
        return service.updatePriceList(id, r);
    }

    @DeleteMapping("/price-lists/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('RETAIL_ADMIN')")
    public void deletePriceList(@PathVariable UUID id) {
        service.deletePriceList(id);
    }

    // -------------------------------------------------------- Price list items
    @GetMapping("/price-lists/{priceListId}/items")
    @PreAuthorize("hasAuthority('RETAIL_VIEW')")
    public List<PriceListItemResponse> listItems(@PathVariable UUID priceListId) {
        return service.listItems(priceListId);
    }

    @PostMapping("/price-lists/{priceListId}/items")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('RETAIL_STORE_MANAGE')")
    public PriceListItemResponse createItem(
            @PathVariable UUID priceListId, @Valid @RequestBody PriceListItemRequest r) {
        return service.createItem(priceListId, r);
    }

    @PutMapping("/price-lists/items/{itemId}")
    @PreAuthorize("hasAuthority('RETAIL_STORE_MANAGE')")
    public PriceListItemResponse updateItem(@PathVariable UUID itemId, @Valid @RequestBody PriceListItemRequest r) {
        return service.updateItem(itemId, r);
    }

    @DeleteMapping("/price-lists/items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('RETAIL_ADMIN')")
    public void deleteItem(@PathVariable UUID itemId) {
        service.deleteItem(itemId);
    }

    // --------------------------------------------------- Store price list assign
    @PostMapping("/store-assignments")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('RETAIL_STORE_MANAGE')")
    public void assignStorePriceList(@RequestParam UUID storeId, @RequestParam UUID priceListId) {
        service.assignStorePriceList(storeId, priceListId);
    }

    // ------------------------------------------------------------- Resolve price
    @GetMapping("/resolve")
    @PreAuthorize("hasAuthority('RETAIL_VIEW')")
    public ResolvePriceResponse resolvePrice(
            @RequestParam UUID storeId,
            @RequestParam UUID productId,
            @RequestParam(required = false) UUID variantId,
            @RequestParam(required = false) BigDecimal qty) {
        return service.resolvePrice(storeId, productId, variantId, qty);
    }

    // --------------------------------------------------------------- Promotions
    @GetMapping("/promotions")
    @PreAuthorize("hasAuthority('RETAIL_VIEW')")
    public List<PromotionResponse> listPromotions() {
        return service.listPromotions();
    }

    @GetMapping("/promotions/{id}")
    @PreAuthorize("hasAuthority('RETAIL_VIEW')")
    public PromotionResponse getPromotion(@PathVariable UUID id) {
        return service.getPromotion(id);
    }

    @PostMapping("/promotions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('RETAIL_STORE_MANAGE')")
    public PromotionResponse createPromotion(@Valid @RequestBody PromotionRequest r) {
        return service.createPromotion(r);
    }

    @PutMapping("/promotions/{id}")
    @PreAuthorize("hasAuthority('RETAIL_STORE_MANAGE')")
    public PromotionResponse updatePromotion(@PathVariable UUID id, @Valid @RequestBody PromotionRequest r) {
        return service.updatePromotion(id, r);
    }

    @DeleteMapping("/promotions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('RETAIL_ADMIN')")
    public void deletePromotion(@PathVariable UUID id) {
        service.deletePromotion(id);
    }

    @PostMapping("/apply-coupon")
    @PreAuthorize("hasAuthority('RETAIL_POS')")
    public ApplyCouponResponse applyCoupon(@Valid @RequestBody ApplyCouponRequest r) {
        return service.applyCoupon(r);
    }
}
