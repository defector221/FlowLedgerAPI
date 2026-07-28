package com.flowledger.storage;

import com.flowledger.common.exception.ResourceNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Dev fallback when MinIO is disabled or unavailable. */
@Service
@ConditionalOnProperty(name = "flowledger.storage.minio.enabled", havingValue = "false")
public class LocalFilesystemStorageService implements StorageService {
    private static final Logger log = LoggerFactory.getLogger(LocalFilesystemStorageService.class);

    private final Path baseDir;

    public LocalFilesystemStorageService(LocalStorageProperties properties) {
        baseDir = Path.of(properties.getBasePath()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(baseDir);
            log.warn("Using local filesystem storage at {} (MinIO disabled)", baseDir);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create local storage directory: " + baseDir, e);
        }
    }

    @Override
    public String store(String objectKey, MultipartFile file) {
        try {
            try (InputStream in = file.getInputStream()) {
                return store(objectKey, in, file.getContentType(), file.getSize());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store object: " + objectKey, e);
        }
    }

    @Override
    public String store(String objectKey, InputStream inputStream, String contentType, long size) {
        try {
            Path target = resolve(objectKey);
            Files.createDirectories(target.getParent());
            Files.copy(inputStream, target);
            return objectKey;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store object: " + objectKey, e);
        }
    }

    @Override
    public InputStream get(String objectKey) {
        Path target = resolve(objectKey);
        if (!Files.isRegularFile(target)) {
            throw new ResourceNotFoundException("Object not found");
        }
        try {
            return Files.newInputStream(target);
        } catch (IOException e) {
            throw new ResourceNotFoundException("Object not found");
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            Files.deleteIfExists(resolve(objectKey));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to delete object: " + objectKey, e);
        }
    }

    @Override
    public String getPresignedUrl(String objectKey, Duration expiry) {
        return resolve(objectKey).toUri().toString();
    }

    private Path resolve(String objectKey) {
        Path resolved = baseDir.resolve(objectKey).normalize();
        if (!resolved.startsWith(baseDir)) {
            throw new IllegalArgumentException("Invalid object key");
        }
        return resolved;
    }
}
