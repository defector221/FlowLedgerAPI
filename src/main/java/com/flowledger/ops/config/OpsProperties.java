package com.flowledger.ops.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "flowledger.ops")
public class OpsProperties {
    private Jwt jwt = new Jwt();

    @Getter
    @Setter
    public static class Jwt {
        /** Separate from tenant JWT secret when set; falls back to tenant secret if blank. */
        private String secret = "";

        private int accessTokenExpiryMinutes = 60;
        private int refreshTokenExpiryDays = 14;
    }
}
