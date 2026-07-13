package com.flowledger.storage;

import java.io.InputStream;
import java.time.Duration;
import org.springframework.web.multipart.MultipartFile;

public interface StorageService {
    String store(String objectKey, MultipartFile file);

    InputStream get(String objectKey);

    void delete(String objectKey);

    String getPresignedUrl(String objectKey, Duration expiry);
}
