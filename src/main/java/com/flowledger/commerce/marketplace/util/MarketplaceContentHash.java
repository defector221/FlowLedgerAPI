package com.flowledger.commerce.marketplace.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class MarketplaceContentHash {
    private MarketplaceContentHash() {}

    public static String hash(ObjectMapper mapper, Object value) {
        try {
            String json = mapper.writeValueAsString(value);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(json.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return String.valueOf(value.hashCode());
        }
    }
}
