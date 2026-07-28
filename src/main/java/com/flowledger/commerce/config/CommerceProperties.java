package com.flowledger.commerce.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "flowledger.commerce")
public class CommerceProperties {
    private Jwt jwt = new Jwt();
    private Otp otp = new Otp();
    private long healthCheckIntervalMs = 300_000L;
    private Marketplace marketplace = new Marketplace();

    @Getter
    @Setter
    public static class Marketplace {
        private Search search = new Search();
        private long imageUrlTtlMinutes = 1440;
        private Sync sync = new Sync();

        @Getter
        @Setter
        public static class Search {
            private String backend = "opensearch";
            private String index = "flowledger-marketplace-search-v1";
        }

        @Getter
        @Setter
        public static class Sync {
            private int maxRetries = 5;
            private int batchSize = 100;
            private long pollIntervalMs = 60_000L;
        }
    }

    @Getter
    @Setter
    public static class Jwt {
        private String secret = "";
        private long accessTokenExpiryMinutes = 30;
        private long refreshTokenExpiryDays = 14;
    }

    @Getter
    @Setter
    public static class Otp {
        private boolean devReturnInResponse = true;
        private int ttlSeconds = 300;
        private String stubCode = "000000";
    }
}
