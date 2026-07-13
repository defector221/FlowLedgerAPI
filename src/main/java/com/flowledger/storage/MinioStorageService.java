package com.flowledger.storage;
import com.flowledger.common.exception.ResourceNotFoundException; import io.minio.*; import io.minio.http.Method; import jakarta.annotation.PostConstruct; import lombok.SneakyThrows; import org.springframework.stereotype.Service; import org.springframework.web.multipart.MultipartFile; import java.io.*; import java.time.Duration;
@Service public class MinioStorageService implements StorageService {
 private final MinioClient client;private final MinioStorageProperties p;public MinioStorageService(MinioClient c,MinioStorageProperties p){client=c;this.p=p;}
 @PostConstruct @SneakyThrows public void ensureBucket(){if(!client.bucketExists(BucketExistsArgs.builder().bucket(p.getBucket()).build()))client.makeBucket(MakeBucketArgs.builder().bucket(p.getBucket()).build());}
 @SneakyThrows public String store(String key,MultipartFile f){try(InputStream in=f.getInputStream()){client.putObject(PutObjectArgs.builder().bucket(p.getBucket()).object(key).stream(in,f.getSize(),-1).contentType(f.getContentType()).build());return key;}}
 @SneakyThrows public InputStream get(String key){try{return client.getObject(GetObjectArgs.builder().bucket(p.getBucket()).object(key).build());}catch(Exception e){throw new ResourceNotFoundException("Object not found");}}
 @SneakyThrows public void delete(String key){client.removeObject(RemoveObjectArgs.builder().bucket(p.getBucket()).object(key).build());}
 @SneakyThrows public String getPresignedUrl(String key,Duration d){return client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder().bucket(p.getBucket()).object(key).method(Method.GET).expiry((int)d.toSeconds()).build());}
}
