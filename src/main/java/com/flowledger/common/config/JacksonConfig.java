package com.flowledger.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.hibernate6.Hibernate6Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

@Configuration
public class JacksonConfig {
    @Bean
    ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
        Hibernate6Module hibernateModule = new Hibernate6Module();
        // open-in-view is false — never force lazy loads during JSON write
        hibernateModule.disable(Hibernate6Module.Feature.FORCE_LAZY_LOADING);
        hibernateModule.enable(Hibernate6Module.Feature.SERIALIZE_IDENTIFIER_FOR_LAZY_NOT_LOADED_OBJECTS);
        return builder
                .modules(new JavaTimeModule(), hibernateModule)
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }
}
