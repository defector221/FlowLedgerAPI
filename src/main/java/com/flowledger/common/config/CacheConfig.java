package com.flowledger.common.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.TimeUnit;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig {
    @Bean
    CacheManager cacheManager() {
        CaffeineCacheManager manager =
                new CaffeineCacheManager("coaTree", "currentFiscalYear", "orgSettings", "systemAccounts");
        manager.setCaffeine(
                Caffeine.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES).maximumSize(500));
        return manager;
    }
}
