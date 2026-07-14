package com.flowledger.search.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "flowledger.search")
public class SearchProperties {
    private boolean enabled = true;
    private String url = "https://localhost:19200";
    private String index = "flowledger-global-search-v1";
    private String username = "admin";
    private String password = "";
    /** Local OpenSearch often uses a self-signed cert; disable only for development. */
    private boolean sslVerify = false;
}
