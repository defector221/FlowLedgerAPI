package com.flowledger.demo.config;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "flowledger.demo")
public class DemoProperties {
    private boolean enabled = false;
    private boolean seedOnStartup = false;
    private String scenario = "retail-small";
    private String defaultPassword = "Password@123";
    private boolean allowReset = false;
    /** Subscription plan code for Demo Center seeded tenants (BUSINESS = Pro). */
    private String planCode = "BUSINESS";
    /** Optional numeric overrides applied on top of blueprint (branchCount, storeCount, productCount, …). */
    private Map<String, Integer> overrides = new HashMap<>();
}
