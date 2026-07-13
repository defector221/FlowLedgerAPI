package com.flowledger;

import com.flowledger.common.security.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({JwtProperties.class, com.flowledger.storage.MinioStorageProperties.class})
public class FlowLedgerApplication {
    public static void main(String[] args) {
        SpringApplication.run(FlowLedgerApplication.class, args);
    }
}
