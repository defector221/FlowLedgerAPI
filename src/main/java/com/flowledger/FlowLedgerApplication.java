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
    com.flowledger.search.config.SearchProperties.class,
    com.flowledger.subscription.config.BillingProperties.class,
    com.flowledger.subscription.config.RazorpayProperties.class,
    com.flowledger.subscription.config.StripeProperties.class,
    com.flowledger.subscription.config.CashfreeProperties.class,
    com.flowledger.subscription.config.PayPalProperties.class,
    com.flowledger.ai.config.AiProperties.class,
    com.flowledger.finance.config.FinanceProperties.class
})
public class FlowLedgerApplication {
    public static void main(String[] args) {
        SpringApplication.run(FlowLedgerApplication.class, args);
    }
}
