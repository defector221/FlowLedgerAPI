package com.flowledger.commerce.validation;

import com.flowledger.commerce.cart.entity.CommerceCart;
import com.flowledger.commerce.cart.entity.CommerceCartItem;
import com.flowledger.commerce.config.CommerceProperties;
import com.flowledger.commerce.customer.entity.CommerceCustomer;
import com.flowledger.commerce.customer.repository.CommerceCustomerRepository;
import com.flowledger.commerce.onboarding.repository.MerchantOnboardingRepository;
import com.flowledger.commerce.publisher.repository.MarketplaceProductIndexRepository;
import com.flowledger.commerce.reservation.CommerceInventoryReservationService;
import com.flowledger.commerce.store.entity.StoreCommerceProfile;
import com.flowledger.commerce.store.repository.StoreCommerceProfileRepository;
import com.flowledger.common.exception.BusinessException;
import com.flowledger.common.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CartValidationService {
    private final StoreCommerceProfileRepository storeProfiles;
    private final MerchantOnboardingRepository onboardingRepository;
    private final MarketplaceProductIndexRepository productIndexRepository;
    private final CommerceCustomerRepository customers;
    private final CommerceProperties properties;

    public CartValidationService(
            StoreCommerceProfileRepository storeProfiles,
            MerchantOnboardingRepository onboardingRepository,
            MarketplaceProductIndexRepository productIndexRepository,
            CommerceCustomerRepository customers,
            CommerceProperties properties) {
        this.storeProfiles = storeProfiles;
        this.onboardingRepository = onboardingRepository;
        this.productIndexRepository = productIndexRepository;
        this.customers = customers;
        this.properties = properties;
    }

    public CartValidationResult validate(CommerceCart cart, List<CommerceCartItem> items) {
        List<String> errors = new ArrayList<>();

        StoreCommerceProfile profile = storeProfiles
                .findByStoreId(cart.getStoreId())
                .orElseThrow(() -> new ResourceNotFoundException("Store commerce profile not found"));
        if (!profile.isCommerceEnabled() || !profile.isAcceptOnlineOrders()) {
            errors.add("Store is not accepting online orders");
        }

        onboardingRepository.findByOrganizationId(cart.getOrganizationId()).ifPresent(onboarding -> {
            if (!onboarding.isLive()) {
                errors.add("Merchant is not live");
            }
        });

        CommerceCustomer customer = customers
                .findById(cart.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        try {
            customer.assertActive();
        } catch (BusinessException ex) {
            errors.add(ex.getMessage());
        }

        if (items.isEmpty()) {
            errors.add("Cart is empty");
        }

        for (CommerceCartItem item : items) {
            if (item.getQuantity().compareTo(BigDecimal.valueOf(properties.getCart().getMaxItemQty())) > 0) {
                errors.add("Quantity exceeds limit for product " + item.getProductId());
            }
            productIndexRepository
                    .findByStoreIdAndProductId(cart.getStoreId(), item.getProductId())
                    .filter(p -> p.isPublished())
                    .orElseGet(() -> {
                        errors.add("Product not available: " + item.getProductId());
                        return null;
                    });
        }

        return new CartValidationResult(errors.isEmpty(), errors);
    }

    public record CartValidationResult(boolean valid, List<String> errors) {}
}
