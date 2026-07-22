package com.flowledger.storage;

import com.flowledger.common.exception.ResourceNotFoundException;
import io.minio.*;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import java.io.*;
import java.time.Duration;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MinioStorageService implements StorageService {
    private final MinioClient client;
    private final MinioStorageProperties properties;

    public MinioStorageService(MinioClient client, MinioStorageProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @PostConstruct
    @SneakyThrows
    public void ensureBucket() {
        if (!client.bucketExists(
                BucketExistsArgs.builder().bucket(properties.getBucket()).build()))
            client.makeBucket(
                    MakeBucketArgs.builder().bucket(properties.getBucket()).build());
    }

    @SneakyThrows
    public String store(String key, MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            client.putObject(
                    PutObjectArgs.builder().bucket(properties.getBucket()).object(key).stream(in, file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build());
            return key;
        }
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
