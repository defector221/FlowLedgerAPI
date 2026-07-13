package com.flowledger.storage;

import io.minio.MinioClient;
import org.springframework.context.annotation.*;

@Configuration
public class StorageConfig {
    @Bean
    MinioClient minioClient(MinioStorageProperties p) {
        return MinioClient.builder()
                .endpoint(p.getEndpoint())
                .credentials(p.getAccessKey(), p.getSecretKey())
                .build();
    }
}
