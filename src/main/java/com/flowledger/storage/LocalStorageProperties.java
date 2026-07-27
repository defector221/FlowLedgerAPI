package com.flowledger.storage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "flowledger.storage.local")
public class LocalStorageProperties {
    private String basePath = System.getProperty("user.home") + "/.flowledger/storage";
}
