package com.flowledger.retail.service;

import static com.flowledger.retail.dto.RetailDtos.*;

import com.flowledger.common.tenant.TenantContext;
import com.flowledger.product.entity.Product;
import com.flowledger.product.repository.ProductRepository;
import com.flowledger.retail.domain.RetailEnums.PriceType;
import com.flowledger.retail.domain.RetailEnums.PromoType;
import com.flowledger.retail.entity.RetailPriceList;
import com.flowledger.retail.entity.RetailPriceListItem;
import com.flowledger.retail.entity.RetailProductVariant;
import com.flowledger.retail.entity.RetailPromotion;
import com.flowledger.retail.entity.RetailStorePriceList;
import com.flowledger.retail.repository.RetailPriceListItemRepository;
import com.flowledger.retail.repository.RetailPriceListRepository;
import com.flowledger.retail.repository.RetailProductVariantRepository;
import com.flowledger.retail.repository.RetailPromotionRepository;
import com.flowledger.retail.repository.RetailStorePriceListRepository;
import com.flowledger.retail.repository.RetailStoreRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class RetailPricingService {
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final RetailModuleGuard guard;
    private final RetailPriceListRepository priceLists;
    private final RetailPriceListItemRepository items;
    private final RetailStorePriceListRepository storePriceLists;
    private final RetailPromotionRepository promotions;
    private final RetailStoreRepository stores;
    private final ProductRepository products;
    private final RetailProductVariantRepository variants;

    public RetailPricingService(
            RetailModuleGuard guard,
            RetailPriceListRepository priceLists,
            RetailPriceListItemRepository items,
            RetailStorePriceListRepository storePriceLists,
            RetailPromotionRepository promotions,
            RetailStoreRepository stores,
            ProductRepository products,
            RetailProductVariantRepository variants) {
        this.guard = guard;
        this.priceLists = priceLists;
        this.items = items;
        this.storePriceLists = storePriceLists;
        this.promotions = promotions;
        this.stores = stores;
        this.products = products;
        this.variants = variants;
    }

    // ------------------------------------------------------------- Price lists
    @Transactional(readOnly = true)
    public List<PriceListResponse> listPriceLists() {
        return priceLists.findByOrganizationIdAndDeletedFalseOrderByNameAsc(org()).stream()
                .map(this::map)
                .toList();
    }

    @Transactional(readOnly = true)
    public PriceListResponse getPriceList(UUID id) {
        return map(loadPriceList(id));
    }

    public PriceListResponse createPriceList(PriceListRequest r) {
        String code = code(r.code());
        if (priceLists.existsByOrganizationIdAndCodeIgnoreCaseAndDeletedFalse(org(), code)) {
            conflict("Price list code already exists");
        }
        RetailPriceList e = new RetailPriceList();
        e.setOrganizationId(org());
        e.setCode(code);
        e.setName(r.name());
        e.setPriceType(r.priceType() == null ? PriceType.RETAIL : r.priceType());
        e.setCurrency(r.currency() == null || r.currency().isBlank() ? "INR" : r.currency());
        e.setActive(r.active() == null || r.active());
        audit(e, true);
        return map(priceLists.save(e));
    }

    public PriceListResponse updatePriceList(UUID id, PriceListRequest r) {
        RetailPriceList e = loadPriceList(id);
        e.setName(r.name());
        if (r.priceType() != null) {
            e.setPriceType(r.priceType());
        }
        if (r.currency() != null && !r.currency().isBlank()) {
            e.setCurrency(r.currency());
        }
        if (r.active() != null) {
            e.setActive(r.active());
        }
        audit(e, false);
        return map(priceLists.save(e));
    }

    public void deletePriceList(UUID id) {
        RetailPriceList e = loadPriceList(id);
        e.setDeleted(true);
        audit(e, false);
    }

    // -------------------------------------------------------- Price list items
    @Transactional(readOnly = true)
    public List<PriceListItemResponse> listItems(UUID priceListId) {
        loadPriceList(priceListId);
        return items.findByOrganizationIdAndPriceListId(org(), priceListId).stream()
                .map(this::map)
                .toList();
    }

    public PriceListItemResponse createItem(UUID priceListId, PriceListItemRequest r) {
        loadPriceList(priceListId);
        RetailPriceListItem e = new RetailPriceListItem();
        e.setOrganizationId(org());
        e.setPriceListId(priceListId);
        e.setProductId(r.productId());
        e.setVariantId(r.variantId());
        e.setUnitPrice(r.unitPrice());
        e.setMinQty(r.minQty() == null ? BigDecimal.ONE : r.minQty());
        audit(e, true);
        return map(items.save(e));
    }

    public PriceListItemResponse updateItem(UUID itemId, PriceListItemRequest r) {
        RetailPriceListItem e = items.findByIdAndOrganizationId(itemId, org())
                .orElseThrow(() -> notFound("Price list item not found"));
        e.setProductId(r.productId());
        e.setVariantId(r.variantId());
        e.setUnitPrice(r.unitPrice());
        e.setMinQty(r.minQty() == null ? BigDecimal.ONE : r.minQty());
        audit(e, false);
        return map(items.save(e));
    }

    public void deleteItem(UUID itemId) {
        RetailPriceListItem e = items.findByIdAndOrganizationId(itemId, org())
                .orElseThrow(() -> notFound("Price list item not found"));
        items.delete(e);
    }

    // --------------------------------------------------- Store price list assign
    public void assignStorePriceList(UUID storeId, UUID priceListId) {
        stores.findByIdAndOrganizationIdAndDeletedFalse(storeId, org())
                .orElseThrow(() -> notFound("Store not found"));
        loadPriceList(priceListId);
        Optional<RetailStorePriceList> existing =
                storePriceLists.findByOrganizationIdAndStoreIdAndPriceListId(org(), storeId, priceListId);
        if (existing.isPresent()) {
            return;
        }
        // Replace prior assignments so each store has a clear primary list.
        storePriceLists.deleteByOrganizationIdAndStoreId(org(), storeId);
        RetailStorePriceList link = new RetailStorePriceList();
        link.setOrganizationId(org());
        link.setStoreId(storeId);
        link.setPriceListId(priceListId);
        storePriceLists.save(link);
    }

    // ------------------------------------------------------------- Resolve price
    @Transactional(readOnly = true)
    public ResolvePriceResponse resolvePrice(UUID storeId, UUID productId, UUID variantId, BigDecimal qty) {
        BigDecimal quantity = qty == null || qty.signum() <= 0 ? BigDecimal.ONE : qty;

        List<RetailStorePriceList> links = storePriceLists.findByOrganizationIdAndStoreId(org(), storeId);
        for (RetailStorePriceList link : links) {
            Optional<RetailPriceListItem> match = findBestItem(link.getPriceListId(), productId, variantId, quantity);
            if (match.isPresent()) {
                RetailPriceListItem item = match.get();
                return new ResolvePriceResponse(productId, variantId, item.getUnitPrice(), "PRICE_LIST");
            }
        }

        if (variantId != null) {
            Optional<RetailProductVariant> variant =
                    variants.findByIdAndOrganizationIdAndDeletedFalse(variantId, org());
            if (variant.isPresent() && variant.get().getSellingPrice() != null) {
                return new ResolvePriceResponse(
                        productId, variantId, variant.get().getSellingPrice(), "VARIANT");
            }
        }

        Product product = products
                .findByIdAndOrganizationId(productId, org())
                .orElseThrow(() -> notFound("Product not found"));
        BigDecimal fallback = product.getSellingPrice() == null ? BigDecimal.ZERO : product.getSellingPrice();
        return new ResolvePriceResponse(productId, variantId, fallback, "PRODUCT");
    }

    private Optional<RetailPriceListItem> findBestItem(
            UUID priceListId, UUID productId, UUID variantId, BigDecimal qty) {
        return items.findByOrganizationIdAndPriceListIdAndProductId(org(), priceListId, productId).stream()
                .filter(i -> i.getMinQty() == null || i.getMinQty().compareTo(qty) <= 0)
                .filter(i -> variantId == null
                        || i.getVariantId() == null
                        || Objects.equals(i.getVariantId(), variantId))
                .sorted(Comparator
                        .comparing((RetailPriceListItem i) ->
                                variantId != null && Objects.equals(i.getVariantId(), variantId) ? 0 : 1)
                        .thenComparing(
                                (RetailPriceListItem i) -> i.getMinQty() == null ? BigDecimal.ZERO : i.getMinQty(),
                                Comparator.reverseOrder()))
                .findFirst();
    }

    // --------------------------------------------------------------- Promotions
    @Transactional(readOnly = true)
    public List<PromotionResponse> listPromotions() {
        return promotions.findByOrganizationIdAndDeletedFalseOrderByNameAsc(org()).stream()
                .map(this::map)
                .toList();
    }

    @Transactional(readOnly = true)
    public PromotionResponse getPromotion(UUID id) {
        return map(loadPromotion(id));
    }

    public PromotionResponse createPromotion(PromotionRequest r) {
        String code = code(r.code());
        if (promotions.existsByOrganizationIdAndCodeIgnoreCaseAndDeletedFalse(org(), code)) {
            conflict("Promotion code already exists");
        }
        RetailPromotion e = new RetailPromotion();
        e.setOrganizationId(org());
        e.setCode(code);
        applyPromotion(e, r);
        audit(e, true);
        return map(promotions.save(e));
    }

    public PromotionResponse updatePromotion(UUID id, PromotionRequest r) {
        RetailPromotion e = loadPromotion(id);
        applyPromotion(e, r);
        audit(e, false);
        return map(promotions.save(e));
    }

    public void deletePromotion(UUID id) {
        RetailPromotion e = loadPromotion(id);
        e.setDeleted(true);
        audit(e, false);
    }

    public ApplyCouponResponse applyCoupon(ApplyCouponRequest r) {
        Optional<RetailPromotion> found = promotions
                .findFirstByOrganizationIdAndCouponCodeIgnoreCaseAndActiveTrueAndDeletedFalse(
                        org(), r.couponCode().trim());
        if (found.isEmpty()) {
            return new ApplyCouponResponse(r.couponCode(), false, BigDecimal.ZERO, r.billAmount(), "Coupon not found");
        }
        RetailPromotion promo = found.get();
        OffsetDateTime now = OffsetDateTime.now();
        if (promo.getStartsAt() != null && now.isBefore(promo.getStartsAt())) {
            return new ApplyCouponResponse(r.couponCode(), false, BigDecimal.ZERO, r.billAmount(), "Coupon not started");
        }
        if (promo.getEndsAt() != null && now.isAfter(promo.getEndsAt())) {
            return new ApplyCouponResponse(r.couponCode(), false, BigDecimal.ZERO, r.billAmount(), "Coupon expired");
        }
        if (promo.getMinBillAmount() != null && r.billAmount().compareTo(promo.getMinBillAmount()) < 0) {
            return new ApplyCouponResponse(
                    r.couponCode(),
                    false,
                    BigDecimal.ZERO,
                    r.billAmount(),
                    "Minimum bill amount not met");
        }

        BigDecimal discount = BigDecimal.ZERO;
        if (promo.getPromoType() == PromoType.PERCENT_OFF || promo.getPromoType() == PromoType.COUPON) {
            if (promo.getDiscountPercent() != null) {
                discount = r.billAmount()
                        .multiply(promo.getDiscountPercent())
                        .divide(HUNDRED, 2, RoundingMode.HALF_UP);
            }
        }
        if (promo.getDiscountAmount() != null
                && (promo.getPromoType() == PromoType.AMOUNT_OFF
                        || promo.getPromoType() == PromoType.BILL_DISCOUNT
                        || promo.getPromoType() == PromoType.COUPON)) {
            discount = discount.max(promo.getDiscountAmount());
            if (promo.getDiscountPercent() == null
                    || promo.getPromoType() == PromoType.AMOUNT_OFF
                    || promo.getPromoType() == PromoType.BILL_DISCOUNT) {
                discount = promo.getDiscountAmount();
            }
        }
        if (discount.compareTo(r.billAmount()) > 0) {
            discount = r.billAmount();
        }
        BigDecimal net = r.billAmount().subtract(discount).max(BigDecimal.ZERO);
        return new ApplyCouponResponse(r.couponCode(), true, discount, net, "Coupon applied");
    }

    private void applyPromotion(RetailPromotion e, PromotionRequest r) {
        e.setName(r.name());
        e.setPromoType(r.promoType());
        e.setDiscountPercent(r.discountPercent());
        e.setDiscountAmount(r.discountAmount());
        e.setBuyQty(r.buyQty());
        e.setGetQty(r.getQty());
        e.setMinBillAmount(r.minBillAmount());
        e.setCouponCode(r.couponCode());
        e.setStartsAt(r.startsAt());
        e.setEndsAt(r.endsAt());
        e.setStoreId(r.storeId());
        e.setBrandId(r.brandId());
        e.setCategoryId(r.categoryId());
        e.setProductId(r.productId());
        e.setActive(r.active() == null || r.active());
    }

    // ----------------------------------------------------------------- Helpers
    private RetailPriceList loadPriceList(UUID id) {
        return priceLists
                .findByIdAndOrganizationIdAndDeletedFalse(id, org())
                .orElseThrow(() -> notFound("Price list not found"));
    }

    private RetailPromotion loadPromotion(UUID id) {
        return promotions
                .findByIdAndOrganizationIdAndDeletedFalse(id, org())
                .orElseThrow(() -> notFound("Promotion not found"));
    }

    private PriceListResponse map(RetailPriceList e) {
        return new PriceListResponse(
                e.getId(), e.getCode(), e.getName(), e.getPriceType(), e.getCurrency(), e.isActive(), e.getVersion());
    }

    private PriceListItemResponse map(RetailPriceListItem e) {
        return new PriceListItemResponse(
                e.getId(), e.getPriceListId(), e.getProductId(), e.getVariantId(), e.getUnitPrice(), e.getMinQty());
    }

    private PromotionResponse map(RetailPromotion e) {
        return new PromotionResponse(
                e.getId(),
                e.getCode(),
                e.getName(),
                e.getPromoType(),
                e.getDiscountPercent(),
                e.getDiscountAmount(),
                e.getBuyQty(),
                e.getGetQty(),
                e.getMinBillAmount(),
                e.getCouponCode(),
                e.getStartsAt(),
                e.getEndsAt(),
                e.getStoreId(),
                e.getBrandId(),
                e.getCategoryId(),
                e.getProductId(),
                e.isActive(),
                e.getVersion());
    }

    private UUID org() {
        return guard.ensureEnabled();
    }

    private String code(String provided) {
        return provided.trim().toUpperCase(Locale.ROOT);
    }

    private void audit(com.flowledger.common.entity.AuditedEntity e, boolean created) {
        TenantContext.userId().ifPresent(u -> {
            if (created) {
                e.setCreatedBy(u);
            }
            e.setUpdatedBy(u);
        });
    }

    private ResponseStatusException notFound(String m) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, m);
    }

    private void conflict(String m) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, m);
    }
}
