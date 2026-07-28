package com.flowledger.commerce.cart;

import com.flowledger.commerce.auth.CommerceSecurityContext;
import com.flowledger.commerce.cart.domain.CartStatus;
import com.flowledger.commerce.cart.entity.CommerceCart;
import com.flowledger.commerce.cart.entity.CommerceCartItem;
import com.flowledger.commerce.cart.repository.CommerceCartItemRepository;
import com.flowledger.commerce.cart.repository.CommerceCartRepository;
import com.flowledger.commerce.customer.repository.CommerceCustomerMembershipRepository;
import com.flowledger.commerce.dto.CommerceDtos;
import com.flowledger.commerce.marketplace.MarketplaceSearchService;
import com.flowledger.commerce.marketplace.domain.MarketplaceProduct;
import com.flowledger.commerce.pricing.CommerceLinePricing;
import com.flowledger.commerce.pricing.CommercePricingService;
import com.flowledger.commerce.reservation.CommerceInventoryReservationService;
import com.flowledger.commerce.store.entity.StoreCommerceProfile;
import com.flowledger.commerce.store.repository.StoreCommerceProfileRepository;
import com.flowledger.commerce.events.CartCreatedEvent;
import com.flowledger.platform.event.DomainEventPublisher;
import com.flowledger.common.exception.BusinessException;
import com.flowledger.common.exception.ResourceNotFoundException;
import com.flowledger.retail.entity.RetailStore;
import com.flowledger.retail.repository.RetailStoreRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CartService {
    private final CommerceCartRepository carts;
    private final CommerceCartItemRepository items;
    private final StoreCommerceProfileRepository storeProfiles;
    private final RetailStoreRepository retailStores;
    private final MarketplaceSearchService marketplaceSearch;
    private final CommercePricingService pricing;
    private final CartItemSnapshotBuilder snapshotBuilder;
    private final CommerceInventoryReservationService reservations;
    private final CommerceCustomerMembershipRepository memberships;
    private final CartMapper mapper;
    private final DomainEventPublisher events;

    public CartService(
            CommerceCartRepository carts,
            CommerceCartItemRepository items,
            StoreCommerceProfileRepository storeProfiles,
            RetailStoreRepository retailStores,
            MarketplaceSearchService marketplaceSearch,
            CommercePricingService pricing,
            CartItemSnapshotBuilder snapshotBuilder,
            CommerceInventoryReservationService reservations,
            CommerceCustomerMembershipRepository memberships,
            CartMapper mapper,
            DomainEventPublisher events) {
        this.carts = carts;
        this.items = items;
        this.storeProfiles = storeProfiles;
        this.retailStores = retailStores;
        this.marketplaceSearch = marketplaceSearch;
        this.pricing = pricing;
        this.snapshotBuilder = snapshotBuilder;
        this.reservations = reservations;
        this.memberships = memberships;
        this.mapper = mapper;
        this.events = events;
    }

    public CommerceDtos.CartResponse getOrCreateCart(UUID storeId) {
        UUID customerId = CommerceSecurityContext.currentCustomer().getCustomerId();
        CommerceCart cart = carts.findByCustomerIdAndStoreIdAndStatus(customerId, storeId, CartStatus.ACTIVE)
                .orElseGet(() -> createCart(customerId, storeId));
        return toResponse(cart);
    }

    public CommerceDtos.CartResponse getCart(UUID cartId) {
        UUID customerId = CommerceSecurityContext.currentCustomer().getCustomerId();
        CommerceCart cart = carts.findByIdAndCustomerId(cartId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found"));
        return toResponse(cart);
    }

    public CommerceDtos.CartResponse addItem(UUID cartId, CommerceDtos.AddCartItemRequest request) {
        UUID customerId = CommerceSecurityContext.currentCustomer().getCustomerId();
        CommerceCart cart = carts.findByIdAndCustomerId(cartId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found"));
        if (cart.getStatus() != CartStatus.ACTIVE) {
            throw new BusinessException("Cart is not active");
        }

        MarketplaceProduct product = marketplaceSearch.getProduct(request.productIndexId());
        if (!cart.getStoreId().equals(product.storeId())) {
            throw new BusinessException("Product belongs to a different store");
        }

        BigDecimal qtyToAdd = request.quantity() != null ? request.quantity() : BigDecimal.ONE;

        CommerceCartItem item = items.findByCartIdAndProductId(cart.getId(), product.productId())
                .orElseGet(() -> {
                    CommerceCartItem newItem = new CommerceCartItem();
                    newItem.setCartId(cart.getId());
                    newItem.setProductId(product.productId());
                    return newItem;
                });

        BigDecimal newQty = item.getId() != null ? item.getQuantity().add(qtyToAdd) : qtyToAdd;

        CommerceLinePricing linePricing = pricing.priceLine(
                cart.getOrganizationId(), cart.getStoreId(), product.productId(), null, newQty, null);

        CartItemSnapshotBuilder.Snapshots snaps = snapshotBuilder.build(product, linePricing, newQty);
        item.setQuantity(newQty);
        item.setVariantId(null);
        item.setProductSnapshot(snaps.productSnapshot());
        item.setPriceSnapshot(snaps.priceSnapshot());
        item.setTaxSnapshot(snaps.taxSnapshot());
        item.setPromotionSnapshot(snaps.promotionSnapshot());
        item.setInventorySnapshot(snaps.inventorySnapshot());
        item.setImageSnapshot(snaps.imageSnapshot());
        item.setLineSubtotal(snaps.lineSubtotal());
        item.setLineTax(snaps.lineTax());
        item.setLineTotal(snaps.lineTotal());
        items.save(item);

        if (linePricing.warehouseId() != null) {
            reservations.reserveForItem(
                    cart.getOrganizationId(), cart.getId(), item, linePricing.warehouseId(), newQty);
        }

        recalculateTotals(cart);
        reservations.renewForCart(cart.getOrganizationId(), cart.getId());
        return toResponse(carts.save(cart));
    }

    public CommerceDtos.CartResponse updateItemQuantity(UUID cartId, UUID itemId, BigDecimal quantity) {
        UUID customerId = CommerceSecurityContext.currentCustomer().getCustomerId();
        CommerceCart cart = carts.findByIdAndCustomerId(cartId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found"));
        CommerceCartItem item = items.findByIdAndCartId(itemId, cart.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found"));

        if (quantity == null || quantity.signum() <= 0) {
            return removeItem(cartId, itemId);
        }

        MarketplaceProduct product = marketplaceSearch
                .getProductByStoreAndProduct(cart.getStoreId(), item.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        CommerceLinePricing linePricing = pricing.priceLine(
                cart.getOrganizationId(), cart.getStoreId(), product.productId(), item.getVariantId(), quantity, null);
        CartItemSnapshotBuilder.Snapshots snaps = snapshotBuilder.build(product, linePricing, quantity);
        item.setQuantity(quantity);
        item.setProductSnapshot(snaps.productSnapshot());
        item.setPriceSnapshot(snaps.priceSnapshot());
        item.setTaxSnapshot(snaps.taxSnapshot());
        item.setInventorySnapshot(snaps.inventorySnapshot());
        item.setLineSubtotal(snaps.lineSubtotal());
        item.setLineTax(snaps.lineTax());
        item.setLineTotal(snaps.lineTotal());
        items.save(item);

        if (linePricing.warehouseId() != null) {
            reservations.reserveForItem(
                    cart.getOrganizationId(), cart.getId(), item, linePricing.warehouseId(), quantity);
        }

        recalculateTotals(cart);
        return toResponse(carts.save(cart));
    }

    public CommerceDtos.CartResponse removeItem(UUID cartId, UUID itemId) {
        UUID customerId = CommerceSecurityContext.currentCustomer().getCustomerId();
        CommerceCart cart = carts.findByIdAndCustomerId(cartId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found"));
        CommerceCartItem item = items.findByIdAndCartId(itemId, cart.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found"));
        reservations.releaseForCartItem(cart.getOrganizationId(), itemId);
        items.delete(item);
        recalculateTotals(cart);
        return toResponse(carts.save(cart));
    }

    private CommerceCart createCart(UUID customerId, UUID storeId) {
        StoreCommerceProfile profile = storeProfiles
                .findByStoreId(storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Store not available for commerce"));
        if (!profile.isCommerceEnabled() || !profile.isAcceptOnlineOrders()) {
            throw new BusinessException("Store is not accepting online orders");
        }
        RetailStore store = retailStores
                .findById(storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Store not found"));

        ensureMembership(customerId, store.getOrganizationId());

        CommerceCart cart = new CommerceCart();
        cart.setCustomerId(customerId);
        cart.setOrganizationId(store.getOrganizationId());
        cart.setStoreId(storeId);
        cart.setStatus(CartStatus.ACTIVE);
        cart = carts.save(cart);
        events.publish(new CartCreatedEvent(this, store.getOrganizationId(), customerId, cart.getId()));
        return cart;
    }

    private void ensureMembership(UUID customerId, UUID organizationId) {
        memberships.findByCustomerIdAndOrganizationId(customerId, organizationId).orElseGet(() -> {
            var membership = new com.flowledger.commerce.customer.entity.CommerceCustomerMembership();
            membership.setCustomerId(customerId);
            membership.setOrganizationId(organizationId);
            return memberships.save(membership);
        });
    }

    private void recalculateTotals(CommerceCart cart) {
        List<CommerceCartItem> cartItems = items.findByCartIdOrderByCreatedAtAsc(cart.getId());
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal grand = BigDecimal.ZERO;
        for (CommerceCartItem item : cartItems) {
            subtotal = subtotal.add(item.getLineSubtotal());
            tax = tax.add(item.getLineTax());
            grand = grand.add(item.getLineTotal());
        }
        cart.setSubtotal(subtotal);
        cart.setTaxTotal(tax);
        cart.setGrandTotal(grand);
        cart.setItemCount(cartItems.stream().mapToInt(i -> i.getQuantity().intValue()).sum());
    }

    private CommerceDtos.CartResponse toResponse(CommerceCart cart) {
        List<CommerceCartItem> cartItems = items.findByCartIdOrderByCreatedAtAsc(cart.getId());
        return mapper.toCartResponse(cart, cartItems);
    }
}
