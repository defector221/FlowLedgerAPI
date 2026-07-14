package com.flowledger;

import com.flowledger.common.security.JwtProperties;
import com.flowledger.notification.config.NotificationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
@EnableConfigurationProperties({
    JwtProperties.class,
    NotificationProperties.class,
    com.flowledger.storage.MinioStorageProperties.class,
    com.flowledger.search.config.SearchProperties.class
})
public class FlowLedgerApplication {
    public static void main(String[] args) {
        SpringApplication.run(FlowLedgerApplication.class, args);
    }
}
