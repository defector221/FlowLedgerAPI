package com.flowledger.subscription.integration;

import com.flowledger.common.exception.BusinessException;
import com.flowledger.subscription.config.BillingProperties;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class PaymentProviderRegistry {
    private final BillingProperties billingProperties;
    private final Map<String, PaymentProvider> providers;

    public PaymentProviderRegistry(BillingProperties billingProperties, List<PaymentProvider> providers) {
        this.billingProperties = billingProperties;
        this.providers = providers.stream()
                .collect(Collectors.toMap(p -> p.name().toLowerCase(Locale.ROOT), Function.identity()));
    }

    public PaymentProvider active() {
        String key = billingProperties.getProvider() == null
                ? "razorpay"
                : billingProperties.getProvider().trim().toLowerCase(Locale.ROOT);
        PaymentProvider provider = providers.get(key);
        if (provider == null) {
            throw new BusinessException("Payment provider not configured: " + key);
        }
        return provider;
    }

    public PaymentProvider require(String name) {
        PaymentProvider provider = providers.get(name == null ? "" : name.toLowerCase(Locale.ROOT));
        if (provider == null) {
            throw new BusinessException("Payment provider not found: " + name);
        }
        return provider;
    }
}
