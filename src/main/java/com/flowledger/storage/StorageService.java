package com.flowledger.storage;

import java.io.InputStream;
import java.time.Duration;
import org.springframework.web.multipart.MultipartFile;

public interface StorageService {
    String store(String objectKey, MultipartFile file);

    default String store(String objectKey, InputStream inputStream, String contentType, long size) {
        throw new UnsupportedOperationException("Streaming store not implemented");
    }

    InputStream get(String objectKey);

    void delete(String objectKey);

    String getPresignedUrl(String objectKey, Duration expiry);
}
