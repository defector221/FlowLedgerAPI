package com.flowledger.tax.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "flowledger.tax")
public class TaxEngineProperties {
    private String defaultProvider = "IndiaGST";
    private int roundingScale = 2;
    private String roundingMode = "HALF_UP";
}
