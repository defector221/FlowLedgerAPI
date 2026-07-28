package com.flowledger.demo.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "flowledger.platform.super-admin")
public class PlatformSuperAdminProperties {
    private String email = "superadmin@flowledger.local";
    private String password = "";
    private String firstName = "Super";
    private String lastName = "Admin";
    private boolean resetPasswordOnStartup = false;

    public boolean hasPassword() {
        return password != null && !password.isBlank();
    }
}
