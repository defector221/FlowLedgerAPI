package com.flowledger.storage;

import io.minio.MinioClient;
import org.springframework.context.annotation.*;

@Configuration
public class StorageConfig {
    @Bean
    MinioClient minioClient(MinioStorageProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();
    }
}
