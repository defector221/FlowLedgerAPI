package com.flowledger.subscription.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "flowledger.billing")
public class BillingProperties {
    /** Active payment provider key: razorpay | stripe | cashfree | paypal */
    private String provider = "razorpay";
}
