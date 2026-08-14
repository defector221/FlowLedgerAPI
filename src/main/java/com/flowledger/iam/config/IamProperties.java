package com.flowledger.iam.config;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "flowledger.iam")
public class IamProperties {
    /** When false, legacy HS256 tenant/platform JWTs remain temporary rollback only. */
    private boolean enabled = false;

    private String baseUrl = "https://iam.sankhya.cloud";
    private String productCode = "flowledger";
    /** Confidential BFF client (Authorization Code + PKCE). */
    private String clientId = "flowledger-bff";
    private String clientSecret = "";
    private String redirectUri = "http://localhost:7070/api/v1/auth/iam/callback";
    private String frontendSuccessUrl = "http://localhost:5173/auth/iam/complete";
    private String postLogoutRedirectUri = "http://localhost:5173/login";
    /** Expected access-token audience; empty skips audience check (local only). */
    private String audience = "flowledger-bff";
    /**
     * Optional login bootstrap hint for IAM resolve/login-url only. Not a durable tenant identity —
     * tenant key is organizations.iam_organization_id.
     */
    private String orgSlug = "";
    private UUID organizationId;
    /**
     * Opt-in self-service signup for this product. Must also be enabled on the IAM side;
     * defaults off (invite-only).
     */
    private boolean selfServiceSignupEnabled = false;
    private boolean thinTokenMode = true;
    private long jwksCacheTtlSeconds = 300;
    private long permissionCacheTtlSeconds = 300;
    private boolean cookieSecure = true;
    /** When IAM login-url/resolve fail, build Keycloak URLs from issuers + OIDC discovery. */
    private boolean fallbackDirectKeycloak = true;
    /** Explicit issuer allowlist; tokens outside this set are rejected. */
    private List<String> trustedIssuers = new ArrayList<>(List.of("https://keycloak.sankhya.cloud/realms/sankhya"));

    private Ops ops = new Ops();

    @Getter
    @Setter
    public static class Ops {
        private String clientId = "flowledger-ops-bff";
        private String clientSecret = "";
        private String redirectUri = "http://localhost:7070/api/v1/ops/auth/iam/callback";
        private String frontendSuccessUrl = "http://localhost:5174/auth/iam/complete";
        private String postLogoutRedirectUri = "http://localhost:5174/login";
    }
}
