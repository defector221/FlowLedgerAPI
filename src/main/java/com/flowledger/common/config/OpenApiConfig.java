package com.flowledger.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI openAPI(
            @Value("${flowledger.app.public-api-url:https://apiflowledger.valiantxgroup.com}") String publicApiUrl) {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("FlowLedger API")
                                .version("v1")
                                .description(
                                        "FlowLedger ERP API — Phase 1 foundation including Voucher Engine, Approvals, and Accounting."))
                .servers(List.of(
                        new Server().url(publicApiUrl).description("Cloudflare tunnel"),
                        new Server().url("http://localhost:7070").description("Local")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components()
                        .addSecuritySchemes(
                                "bearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }
}
