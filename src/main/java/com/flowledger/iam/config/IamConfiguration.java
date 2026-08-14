package com.flowledger.iam.config;

import com.flowledger.iam.client.IamClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableConfigurationProperties(IamProperties.class)
public class IamConfiguration {

    @Bean
    RestTemplate iamRestTemplate(RestTemplateBuilder builder) {
        return builder.build();
    }

    @Bean
    IamClient iamClient(IamProperties properties, RestTemplate iamRestTemplate) {
        return new IamClient(properties, iamRestTemplate);
    }
}
