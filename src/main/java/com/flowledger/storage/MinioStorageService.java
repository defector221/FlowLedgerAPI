package com.flowledger.storage;

import com.flowledger.common.exception.ResourceNotFoundException;
import io.minio.*;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import java.io.*;
import java.time.Duration;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@ConditionalOnProperty(name = "flowledger.storage.minio.enabled", havingValue = "true", matchIfMissing = true)
public class MinioStorageService implements StorageService {
    private static final Logger log = LoggerFactory.getLogger(MinioStorageService.class);
    private final MinioClient client;
    private final MinioStorageProperties properties;

    public MinioStorageService(MinioClient client, MinioStorageProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @PostConstruct
    @SneakyThrows
    public void ensureBucket() {
        try {
            if (!client.bucketExists(
                    BucketExistsArgs.builder().bucket(properties.getBucket()).build()))
                client.makeBucket(
                        MakeBucketArgs.builder().bucket(properties.getBucket()).build());
            log.info("MinIO bucket ready: {} at {}", properties.getBucket(), properties.getEndpoint());
        } catch (Exception e) {
            throw new IllegalStateException(
                    "MinIO is not reachable at "
                            + properties.getEndpoint()
                            + ". Start it with `docker compose up -d minio` or set MINIO_ENABLED=false for local filesystem storage.",
                    e);
        }
    }

    @SneakyThrows
    public String store(String key, MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return store(key, in, file.getContentType(), file.getSize());
        }
    }

    @SneakyThrows
    @Override
    public String store(String key, InputStream inputStream, String contentType, long size) {
        client.putObject(
                PutObjectArgs.builder().bucket(properties.getBucket()).object(key).stream(inputStream, size, -1)
                        .contentType(contentType != null ? contentType : "application/octet-stream")
                        .build());
        return key;
    }

    @SneakyThrows
    public InputStream get(String key) {
        try {
            return client.getObject(GetObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(key)
                    .build());
        } catch (Exception e) {
            throw new ResourceNotFoundException("Object not found");
        }
    }

    @SneakyThrows
    public void delete(String key) {
        client.removeObject(RemoveObjectArgs.builder()
                .bucket(properties.getBucket())
                .object(key)
                .build());
    }

    @SneakyThrows
    public String getPresignedUrl(String key, Duration expiry) {
        return client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .bucket(properties.getBucket())
                .object(key)
                .method(Method.GET)
                .expiry((int) expiry.toSeconds())
                .build());
    }
}
