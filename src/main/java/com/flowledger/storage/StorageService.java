package com.flowledger.storage;
import org.springframework.web.multipart.MultipartFile; import java.io.InputStream; import java.time.Duration;
public interface StorageService {String store(String objectKey,MultipartFile file); InputStream get(String objectKey); void delete(String objectKey); String getPresignedUrl(String objectKey,Duration expiry);}
