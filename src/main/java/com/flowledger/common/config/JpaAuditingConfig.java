package com.flowledger.common.config;

import com.flowledger.common.tenant.TenantContext;
import org.springframework.context.annotation.*;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import java.util.UUID;

@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
    @Bean AuditorAware<UUID> auditorAware() { return TenantContext::userId; }
}
