package com.flowledger.subscription.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "flowledger.stripe")
public class StripeProperties {
    private String secretKey = "";
    private String webhookSecret = "";
    private String publishableKey = "";

    public boolean isConfigured() {
        return secretKey != null && !secretKey.isBlank();
    }
}
