package com.flowledger.common.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter @Setter
@ConfigurationProperties(prefix = "flowledger.jwt")
public class JwtProperties {
    private String secret;
    private long accessTokenExpiryMinutes = 30;
    private long refreshTokenExpiryDays = 14;
}
