package com.flowledger.subscription.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "flowledger.cashfree")
public class CashfreeProperties {
    private String appId = "";
    private String secretKey = "";
    private String webhookSecret = "";
    /** sandbox | production */
    private String env = "sandbox";

    public boolean isConfigured() {
        return appId != null && !appId.isBlank() && secretKey != null && !secretKey.isBlank();
    }

    public String apiBaseUrl() {
        return "production".equalsIgnoreCase(env) ? "https://api.cashfree.com/pg" : "https://sandbox.cashfree.com/pg";
    }
}
