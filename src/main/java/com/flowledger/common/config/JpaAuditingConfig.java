package com.flowledger.common.config;

import com.flowledger.common.tenant.TenantContext;
import java.util.UUID;
import org.springframework.context.annotation.*;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
    @Bean
    AuditorAware<UUID> auditorAware() {
        return TenantContext::userId;
    }
}
